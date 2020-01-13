package cantina_test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

public class Cantina {
	private Map<String,Object> inputData = null;
	
	public Cantina(String filename) {
		inputData = parseInput(filename);
	}
	
	@SuppressWarnings("unchecked")
	private Map<String,Object> parseInput(String filename) {
		File file = new File(filename);
		
		Map<String,Object> map = new HashMap<String,Object>();
    	Gson gson = new Gson();
		try {
			return (Map<String,Object>) gson.fromJson(new InputStreamReader(new FileInputStream(file)), map.getClass());
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private void printObject(Map<String,Object> toPrint) {
		Gson gson = new Gson();
		System.out.println(gson.toJson(toPrint));
	}
	
	/*@SuppressWarnings("unchecked")
	private void searchAndPrint(Map<String,Object> currInput, String key, String selector) {
		for (Map.Entry<String,Object> entry : currInput.entrySet()) {
			if (entry.getKey().equals(key)) {
				if ((key.equals("class") || key.equals("identifier")) && ((String)entry.getValue()).equals(selector)){
					printObject(currInput);
				} else {
					if (key.equals("classNames")) {
						List<String> classNames = (List<String>)entry.getValue();
						for (String className : classNames) {
							if (className.equals(selector)) {
								printObject(currInput);
							}
						}
					}
				}
			} else {
				if (entry.getKey().equals("subviews") && entry.getValue() instanceof List) {
					List<Map<String,Object>> subviews = (List<Map<String,Object>>)entry.getValue();
					for (Map<String,Object> subview : subviews) {
						searchAndPrint(subview, key, selector);
					}
				} else {
					if (entry.getValue() instanceof Map) {
						searchAndPrint((Map<String,Object>)entry.getValue(), key, selector);
					}
				}
			}
		}
	}*/
	
	@SuppressWarnings("unchecked")
	private void searchAndPrint(Map<String,Object> currInput, List<Map<String,String>> selectors) {
		String key = selectors.get(0).get("key");
		String selector = selectors.get(0).get("selector");
		
		for (Map.Entry<String,Object> entry : currInput.entrySet()) {
			if (entry.getKey().equals(key)) {
				if ((key.equals("class") || key.equals("identifier")) && ((String)entry.getValue()).equals(selector)){
					if (selectors.size() > 1) {
						searchAndPrint(currInput, removeSelector(selectors));
					} else {
						printObject(currInput);
					}
				} else {
					if (key.equals("classNames")) {
						List<String> classNames = (List<String>)entry.getValue();
						for (String className : classNames) {
							if (className.equals(selector)) {
								if (selectors.size() > 1) {
									searchAndPrint(currInput, removeSelector(selectors));
								} else {
									printObject(currInput);
								}
							}
						}
					}
				}
			} else {
				if (entry.getKey().equals("subviews") && entry.getValue() instanceof List) {
					List<Map<String,Object>> subviews = (List<Map<String,Object>>)entry.getValue();
					for (Map<String,Object> subview : subviews) {
						searchAndPrint(subview, selectors);
					}
				} else {
					if (entry.getValue() instanceof Map) {
						searchAndPrint((Map<String,Object>)entry.getValue(), selectors);
					}
				}
			}
		}
	}
	
	public void processInput(String selector) {
		List<Map<String,String>> selectorList = new ArrayList<>();
		if (selector.startsWith(".") && !selector.contains(" ")) {
			selectorList.add(getSelector("classNames", selector.substring(1)));
		} else {
			if (selector.startsWith("#") && !selector.contains(" ")) {
				selectorList.add(getSelector("identifier", selector.substring(1)));
			} else {
				if (selector.contains("#")) {
					// compound selector - expects only 2 selectors
					String[] selectors = selector.split("#");
					selectorList.add(getSelector("class", selectors[0]));
					selectorList.add(getSelector("identifier", selectors[1]));
				} else {
					if (selector.contains(" ")) {
						// selector chains - expects N selectors
						String[] selectors = selector.split(" ");
						for (String s : selectors) {
							if (s.startsWith(".")) {
								selectorList.add(getSelector("classNames", s.substring(1)));
							} else {
								if (s.startsWith("#")) {
									selectorList.add(getSelector("identifier", s.substring(1)));
								} else {
									selectorList.add(getSelector("class", s));
								}
							}
						}
					} else {
						selectorList.add(getSelector("class", selector));
					}
				}
			}
		}
		searchAndPrint(inputData, selectorList);
	}
	
	private Map<String,String> getSelector(String key, String value) {
		Map<String,String> s = new HashMap<>();
		s.put("key", key);
		s.put("selector", value);
		return s;
	}
	
	private List<Map<String,String>> removeSelector(List<Map<String,String>> currSelectors) {
		List<Map<String,String>> tmp = new ArrayList<>();
		if (currSelectors.size() > 1) {
			for(int i=1; i<currSelectors.size(); i++) {
				tmp.add(getSelector(currSelectors.get(i).get("key"), currSelectors.get(i).get("selector")));
			}
		}
		return tmp;
	}
	
	public static void main( String[] args ) {
		if (args.length != 1) {
			return;
		}
		Cantina c = new Cantina(args[0]);
		try {
	        InputStreamReader isr = new InputStreamReader(System.in);
	        BufferedReader br = new BufferedReader(isr);
	        String line = "";
	        while ((line = br.readLine()) != null && !line.toLowerCase().equals("exit")) {
	        	c.processInput(line);
	        }
	        isr.close();
	    } catch (Exception e) {
	        e.printStackTrace();
	    }
    }
}
