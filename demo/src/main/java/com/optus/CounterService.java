package com.optus;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The main service class. This services two URLs
 * 
 * 1. /counter-api/search = For this URL we return the count of specified words.
 * Input = {\"searchText\":[\"Duis\", \"Sed\", \"Donec\", \"Augue\", \"Pellentesque\", \"123\"], \"resourceName\":\"resource.txt\"}" where resourceName is optional and defaults
 * to a default resource.
 * 
 * It takes a JSON as an input and produces a JSON output listing the occurrences of each word specified in the input.
 * 
 * 2. /counter-api/count/{top}?resourceName=abc = For this URL we return the words with the max occurrences in the text. e.g. if {top} is 10 then most occuring 10 words and their counts
 * It also optionally also takes a resource name and it defaults to the default resource
 * 
 * Output is in text/CSV format
 * 
 * @author Rohan Z
 *
 */
@RestController
public class CounterService {
	
	/**
	 * THe default resource
	 */
	private static final String DEFAULT_RESOURCE = "data.txt";
	
	/**
	 * The cache of processed resources. This processes a resource and stores a map of word occurrences
	 */
	// TODO:: Use a better cache which will clear unused resources.
	private ConcurrentHashMap<String, Map<String, Integer>> resourceCache = new ConcurrentHashMap<String, Map<String, Integer>>();
	
	@Autowired
	ApplicationContext ctx;

	/**
	 * Method that handles the search request
	 * 
	 * @param searchData
	 * @return
	 */
	@RequestMapping(value = "/counter-api/search", method = RequestMethod.POST, consumes = MimeTypeUtils.APPLICATION_JSON_VALUE, produces = MimeTypeUtils.APPLICATION_JSON_VALUE)
	public String search(@RequestBody SearchData searchData) {
		// Log the requested search words
		System.out.println(searchData.getSearchText());
		
		// Extract the resource name
		String resourceName = searchData.getResourceName();
		
		// Default
		if(resourceName == null)
			resourceName = DEFAULT_RESOURCE;
		
		// Process the resource and create a map.
		Map<String, Integer> processedData = getProcessedData(resourceName);

		// Determine the count of the requested words.
		String result = countWords(processedData, searchData.getSearchText());
		
		return result;
	}
	
	/**
	 * Method that handles the top request
	 * 
	 * @param count
	 * @param resourceName
	 * @return
	 */
	@RequestMapping(value = "/counter-api/top/{count}", method = RequestMethod.GET, produces = "text/csv")
	public String search(@PathVariable int count, @RequestParam(name="resourceName", required=false, defaultValue=DEFAULT_RESOURCE) String resourceName) {
		System.out.println("Request top " + count);
		
		Map<String, Integer> processedData = getProcessedData(resourceName);
		String result = top(processedData, count);
		
		return result;
	}

	/**
	 * Method that does the actual top processing and creates the response CSV string.
	 * 
	 * @param counts
	 * @param count
	 * @return
	 */
	private String top(Map<String, Integer> counts, int count) {
        Comparator<Integer> reverseComparator = (e1, e2) -> Integer.compare(e2, e1);
        
        StringBuilder result = new StringBuilder();
        counts.entrySet().stream().sorted(Map.Entry.comparingByValue(reverseComparator)).limit(count).forEach(
        		x -> {
        			result.append(x.getKey());
        			result.append("|");
        			result.append(x.getValue());
        			result.append("\r\n");
        		});
        
        return result.toString();
	}

	/**
	 * The utility method that checks to see if the resource is available in cache or else requests read and process for it.
	 * 
	 * TODO:: WE CAN USE SEMAPHORES TO ALLOW A FIXED NUMBER OF THREADS TO UPDATE A BETTER CACHE IMPLEMENTATION
	 * (CURRENTLY THE CACHE IS A CONCURRENT MAP)
	 * 
	 * @param resourceName
	 * @return
	 */
	private Map<String, Integer> getProcessedData(String resourceName) {
		if(resourceCache.containsKey(resourceName)) {
			System.out.println("Found resource " + resourceName + " in cache");
			return resourceCache.get(resourceName);
		}
		else {
			System.out.println("Processing and cacheing resource " + resourceName);
			String data = readResource(resourceName);
			Map<String, Integer> processedData = processData(data);
			resourceCache.put(resourceName, processedData);
			return processedData;
		}
	}
	
	/**
	 * Split the read string into words array and using parallel streams count every occurrence.
	 * 
	 * @param data
	 * @return
	 */
	private Map<String, Integer> processData(String data) {
		String[] dataArray = data.toString().toLowerCase().split("\\W+");
        
        List<String> list = Arrays.asList(dataArray);
        Map<String, Integer> counts = list.parallelStream().collect(Collectors.toConcurrentMap(w -> w, w -> 1, Integer::sum));
        
        return counts;
	}

	/**
	 * The utility method that retrieves the occurrences of the requested words and creates the response JSON String.
	 * 
	 * @param counts
	 * @param searchText
	 * @return
	 */
	private String countWords(Map<String, Integer> counts, List<String> searchText) {
		StringBuilder result = new StringBuilder("{\"counts\": [");
        
        searchText.forEach(word -> {
        	String key = word.toLowerCase();
        	result.append("{\"");
        	result.append(word);
        	result.append("\": ");
        	int count = counts.containsKey(key) ? counts.get(key) : 0;
            result.append(count);
            result.append("}, ");
        });
        
        result.delete(result.length() - 2, result.length());
        result.append("]}");
        
        return result.toString();
	}
	
	/**
	 * Read the actual resource from the resource directory.
	 * 
	 * TODO:: NEEDS BETTER EXCEPTION HANDLING CURRENTLY DOES NOTHING BUT SIMPLY CONSUMES EXCEPTIONS
	 * 
	 * @param resourceName
	 * @return
	 */
	private String readResource(String resourceName) {
		Resource resource = ctx.getResource("classpath:" + resourceName);

		if (!resource.exists())
			return "";

		StringBuilder tmp = new StringBuilder();

		try {
			BufferedInputStream reader = new BufferedInputStream(resource.getInputStream());
			byte[] buffer = new byte[1024];

			while (reader.read(buffer) > 0) {
				for (int i = 0; i < buffer.length; i++) {
					tmp.append((char) buffer[i]);
				}
				Arrays.fill(buffer, (byte)0);
			}
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		return tmp.toString();
	}
}
