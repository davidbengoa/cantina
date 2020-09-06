package hubspot_test;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

public class Invitations {
	private static String API_KEY = "cd9fcbc703a1c2081f72ec63df14";
	private static String API_URL = "https://candidate.hubteam.com/candidateTest/v3/problem";
	private static String ENDPOINT_GET_PARTNERS = API_URL + "/dataset?userKey=" + API_KEY;
	private static String ENDPOINT_RESULT = API_URL + "/result?userKey=" + API_KEY;
	
	public static String inputStreamtoString(InputStream fi) {
		ByteArrayOutputStream bout = new ByteArrayOutputStream();
		try {
			byte buffer[] = new byte[1000];
			int len;
			while ((len = fi.read(buffer)) != -1 ) {
				bout.write(buffer, 0, len);
			}
			bout.close();
			fi.close();	
		} catch(Exception e) {
			System.err.println("inputStreamtoString: " + e.getMessage());
		}
		return bout.toString();
	}
	
	@SuppressWarnings("unchecked")
	public static Map<String,Object> fromJsonToMap(Reader reader) {
		if (reader == null) {
			return null;
		}
    	Map<String,Object> map = new HashMap<String,Object>();
    	Gson gson = new Gson();
		return (Map<String,Object>) gson.fromJson(reader, map.getClass());
    }
		
	private Reader api(String method, String endpoint, String body) {
		try {
			URL url = new URL(endpoint);
			HttpURLConnection con = (HttpURLConnection) url.openConnection();
			
			con.setRequestMethod(method);
			con.setDoInput(true);
			con.setUseCaches(false);
			con.setRequestProperty("accept", "application/json");
			con.setRequestProperty("content-type", "application/json");
			
			if (!body.isEmpty()) {
				con.setDoOutput(true);
				DataOutputStream wr = new DataOutputStream(con.getOutputStream());
				wr.writeBytes(body);
				wr.flush();
				wr.close();
			}
			
			int responseCode = con.getResponseCode();
			if (responseCode != 200) {
				String message = inputStreamtoString(con.getErrorStream());
				System.err.println("APICall - Error: " + responseCode + ": " + message + "\nBody: " + body + "\nURL: " + url.toString());
				return null;
			} else {
				return new InputStreamReader(con.getInputStream());
			}
		} catch (Exception e) {
			System.err.println("APICall - Error: " + e.getMessage());
		}
		return null;
	}
	
	@SuppressWarnings("unchecked")
	private List<Map<String,Object>> getPartners() {
		Reader reader = api("GET", ENDPOINT_GET_PARTNERS, "");
		Map<String,Object> map = fromJsonToMap(reader);
		if (map != null) {
			return (List<Map<String,Object>>)map.get("partners");
		}
		return null;
	}
	
	private void sendInvitationsList(Map<String,Object> data) {
		Gson gson = new Gson();
		String resultData = gson.toJson(data);
		api("POST", ENDPOINT_RESULT, resultData);
	}
	
	private boolean areConsecutives(String date1, String date2) {
		LocalDate ld1 = LocalDate.parse(date1);
		LocalDate ld2 = LocalDate.parse(date2);
		return Period.between(ld1, ld2).getDays() == 1;
	}
	
	private boolean isFirstEarlierThanSecondDate(String date1, String date2) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
		try {
			Date sdf1 = sdf.parse(date1);
			Date sdf2 = sdf.parse(date2);
			return sdf1.before(sdf2);
		} catch (ParseException e) {
			e.printStackTrace();
			return false;
		}		
	}
	
	@SuppressWarnings("unchecked")
	public void process() {
		List<Map<String,Object>> partners = getPartners();
		if (partners == null) {
			System.out.println("Error pulling the Partners List");
			return;
		}
		
		// Countries --> AvailableDates --> List of Attendees
		Map<String,Map<String,List<String>>> parsedData = new HashMap<>();
		// parse the data
		for(Map<String,Object> partner : partners) {
			List<String> rawAvailableDates = (List<String>)partner.get("availableDates");
			if (rawAvailableDates.size() >= 2) {
				List<String> availableDates = new ArrayList<>();
				String firstDate = "";
				for(String rawAvailableDate : rawAvailableDates) {
					if (!firstDate.isEmpty() && areConsecutives(firstDate, rawAvailableDate)) {
						availableDates.add(firstDate);
					}
					firstDate = rawAvailableDate;
				}
				
				if (!parsedData.containsKey(partner.get("country"))) {
					Map<String,List<String>> newAvailableDates = new HashMap<>();
					for(String availableDate : availableDates) {
						List<String> attendees = new ArrayList<>();
						attendees.add((String)partner.get("email"));
						newAvailableDates.put(availableDate, attendees);
					}
					parsedData.put((String)partner.get("country"), newAvailableDates);
				} else {
					Map<String,List<String>> existingDates = parsedData.get(partner.get("country"));
					for(String availableDate : availableDates) {
						if (existingDates.containsKey(availableDate)) {
							parsedData.get(partner.get("country")).get(availableDate).add((String)partner.get("email"));
						} else {
							List<String> attendees = new ArrayList<>();
							attendees.add((String)partner.get("email"));
							parsedData.get(partner.get("country")).put(availableDate, attendees);
						}
					}
				}
			}
		}
		
		// prepare data to call POST API
		List<Map<String,Object>> finalData = new ArrayList<>();
		parsedData.forEach((country, availableDates) -> {
			Map<String,Object> newCountry = new HashMap<>();
			
			String chosenDate = "";
			List<String> chosenAttendees = null;
			for (Map.Entry<String,List<String>> entry : availableDates.entrySet()) {
				String currDate = entry.getKey();
				List<String> currAttendees = entry.getValue();
				
				if (chosenAttendees == null || chosenAttendees.size() < currAttendees.size() || (
						chosenAttendees.size() == currAttendees.size() && isFirstEarlierThanSecondDate(currDate, chosenDate))) {
					chosenAttendees = currAttendees;
					chosenDate = currDate;
				}
			}
			chosenAttendees = chosenAttendees == null ? new ArrayList<>() : chosenAttendees;
			
			newCountry.put("attendeeCount", chosenAttendees.size());
			newCountry.put("attendees", chosenAttendees);
			newCountry.put("name", country);
			newCountry.put("startDate", chosenDate.isEmpty() ? null : chosenDate);
			finalData.add(newCountry);
		});
		Map<String,Object> extraFinalData = new HashMap<>();
		extraFinalData.put("countries", finalData);
		sendInvitationsList(extraFinalData);
	}
	
	public static void main( String[] args )
    {
        new Invitations().process();
    }
}
