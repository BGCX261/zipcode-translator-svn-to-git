import jargs.gnu.CmdLineParser;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.opera.core.systems.OperaDriver;
import com.opera.core.systems.OperaDriver.PrivateData;

public class Main {
	
	private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
	
	/**
	 * File with input data.
	 */
	private static File inputFile;
	
	/**
	 * How much to wait(ms) after processed a ZIP code before moving on to the next iteration.
	 */
	private static long iterationSleep;
	
	/**
	 * Address of 'coduripostale.ro'.
	 */
	public static final String CODURI_POSTALE = "http://www.posta-romana.ro/posta-romana/servicii-online/Coduri-postale.html";
	
	/**
	 * Google Maps URL that we'll be querying for doing the geocoding. Response format will be XML.
	 */
	public static final String GEOCODER_REQUEST_PREFIX = "http://maps.google.com/maps/api/geocode/xml";
	
	/**
	 * When we encounter an exception when dealing with a web-site, we are waiting this timeout,
	 * before moving on.
	 */
	public static final long EXCEPTION_SLEEP = 5000;

	/**
	 * Result writer.
	 */
	private static ResultWriter resultWriter;
	
	
	public static void main(String[] args) throws IOException {
		getCommandLineArguments(args);

		run();
	}
	
	/**
	 * Run algorithm for retrieving information.
	 * <code>
	 * for each ZIP code
	 *   go to CODURI_POSTALE and get GeoInfo
	 *   query GoogleMaps for coordinates
	 *   write results to file 
	 * </code>
	 * 
	 * @throws IOException if something went wrong during write
	 */
	private static void run() throws IOException {
		resultWriter = new ResultWriter();
		
		for (String zipCode : new InputData(inputFile).getZipCodes()) {
			OperaDriver driver = getOperaDriver();
			try {
				try {
					GeoInfo g = getInformationFromCoduriPostale(driver, zipCode);
					
					if (g != null) {
						getInformationFromGoogleMaps(zipCode, g);
					} else {
						resultWriter.writeInvalid(zipCode);
					}
				} catch (IllegalStateException e) {
					resultWriter.writeError(zipCode, null, null);
				}
			} finally {
				driver.quit();
			}
			
			sleep(iterationSleep);
		}
		resultWriter.showSummary();
	}
	
	/**
	 * Retrieve coordinates info from Google Maps for this GeoInfo. After the first match
	 * we're going to return immediately.
	 * 
	 * @param zipCode ZIP code
	 * @param g GeoInfo object we want to geocode 
	 * @throws IOException if something goes wrong during I/O or we have reached the maximum number of queries
	 */
	private static void getInformationFromGoogleMaps(String zipCode, GeoInfo g) throws IOException {
		
		final String county = g.getCounty();
		final String locality = g.getLocality();

		List<String> list = new ArrayList<String>();
		if (g.getStreets() != null) {
			list.addAll(g.getStreets());
		}
		list.add(locality);
		
		for (int j = 0; j < list.size(); j++) {
			final String street = j == list.size() - 1 ? null : list.get(j);				
			final String address = getSearchAddress(county, locality, street);
			
			try {
				if (geocode(address, g)) {
					resultWriter.writeResult(zipCode, g, street);
					break;
				}
			} catch (Exception e) {
				LOGGER.warning(e.toString());
				if (e.getMessage().startsWith("Maximum number of queries reached!")) {
					resultWriter.showSummary();
					throw new IOException(e);
				}
			}
		}
	}
			
	private static boolean geocode(String address, GeoInfo g) throws IOException, MalformedURLException, XPathExpressionException, UnsupportedEncodingException {
		// prepare a URL to the geocoder
		//LOGGER.info("Querying for: " + address);
	    URL url = new URL(String.format("%s?address=%s&components=country:RO&sensor=false",
	    		GEOCODER_REQUEST_PREFIX, URLEncoder.encode(address, "UTF-8")));
	    
	    // prepare an HTTP connection to the geocoder
	    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
	    
	    Document geocoderResultDocument = null;
	    try {
	      // open the connection and get results as InputSource.
	      conn.connect();
	      InputSource geocoderResultInputSource = new InputSource(conn.getInputStream());

	      // read result and parse into XML Document
	      geocoderResultDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(geocoderResultInputSource);
	    } catch (Exception e) {
	    	return false;
	    } finally {
	      conn.disconnect();
	    }
	    
	    // prepare XPath
	    XPath xpath = XPathFactory.newInstance().newXPath();
	    
	    NodeList resultNodeList = (NodeList) xpath.evaluate("/GeocodeResponse/status", geocoderResultDocument, XPathConstants.NODESET);
	    if (resultNodeList.getLength() > 0) {
	    	Node node = resultNodeList.item(0);
	    	final String status = node.getTextContent(); 
	    	if (status.equals("OK")) {
	    		// Move on
	    	} else if (status.equals("OVER_QUERY_LIMIT")) {
	    		LOGGER.warning("Maximum number of queries reached! Limit is 2500 queries / 24 h.");
	    		throw new IOException("Maximum number of queries reached! Limit is 2500 queries / 24 h.");
	    	} else {
	    		LOGGER.warning(String.format("%s returned when querying for: ", status, address));
	    		return false;
	    	}
	    } else {
	    	LOGGER.warning("GoogleMaps's response contains no status!");
	    	return false;
	    }
	    
	    // extract the coordinates of the first result
	    resultNodeList = (NodeList) xpath.evaluate("/GeocodeResponse/result[1]/geometry/location/*", geocoderResultDocument, XPathConstants.NODESET);
	    for(int i = 0; i < resultNodeList.getLength(); ++i) {
	      Node node = resultNodeList.item(i);
	      final String nodeName = node.getNodeName();
	      
	      if (nodeName.equals("lat")) {
	    	  g.setLatitude(node.getTextContent());
	      }
	      if (nodeName.equals("lng")) {
	    	  g.setLongitude(node.getTextContent());
	      }
	      
	      if (g.hasCoordinates()) {
	    	  return true;
	      }
	    }
	   
	    
	    return g.hasCoordinates();
	}
	
	/**
	 * Get the string that we'll be searching on Google Maps.
	 * 
	 * @param county name
	 * @param locality name
	 * @param street name(can be null)
	 * @return address that we'll search on Google Maps
	 */
	private static String getSearchAddress(String county, String locality, String street) {
		// Order of search strings matters. The importance level decreases from left to
		// right, thus the most accurate element has to be first.
		String search;
		// We are dealing with a small locality
		if (street == null) {
			search = locality + " " + county;
		} else {
			search = cleanupStreetName(street) + " " + locality;
		}

		return search;
	}
	
	/**
	 * Return a geographical information object containing the county, locality and street(s) 
	 * information corresponding to the provided ZIP code.
	 * 
	 * @param driver OperaDriver
	 * @param zipCode ZIP code
	 * @return GeoInfo object if successful information retrieved or null if ZIP code is invalid
	 * @throws IllegalStateException if we could not gather information about this ZIP code
	 */
	private static GeoInfo getInformationFromCoduriPostale(OperaDriver driver, String zipCode) {
		driver.navigate().to(CODURI_POSTALE);

		// In case something goes wrong, try to repeat the procedure once
		for (int i = 0; i < 2; i++) {
			try {
				//LOGGER.info("Checking zip code: " + zipCode);
		
				WebElement el = driver.findElementByLinkText("Cautare dupa cod postal");
				el.click();
				
				el = driver.findElementById("postalCode");
				el.clear();
				el.sendKeys(zipCode);
				
				el = driver.findElementByXPath("//input[contains(@onclick, 'findAddressByPostalCode')]");
				el.click();
				
				// Wait for results to be displayed
				Thread.sleep(500);
				
				el = driver.findElementById("searchResults");
				
				return getGeoInfoFromCoduriPostaleSearchResult(el);
			} catch (Exception e) {
				LOGGER.warning(e.toString());
				e.printStackTrace();
				
				handleNavigationException(driver, CODURI_POSTALE);
			}
		}
		
		throw new IllegalStateException("Could not gather either valid or invalid GeoInfo about ZIP code " + zipCode);
	}
	
	/**
	 * Parse the WebElement representing the search results div from coduri postale.
	 * 
	 * @param el WebElement representing the search results div
	 * @return GeoInfo object if we successfully retrieved data or null if not
	 */
	private static GeoInfo getGeoInfoFromCoduriPostaleSearchResult(WebElement el) {
		GeoInfo g = new GeoInfo();
		WebElement table = (el.findElements(By.tagName("table"))).get(0);
		
		List<WebElement> rows = table.findElements(By.tagName("tr"));
		for (int i = 0; i < rows.size(); i++) {
			// Skip table header
			if (i > 0) {
				String text;
				try {
					text = (rows.get(i).findElements(By.tagName("td"))).get(1).getText();
				} catch (IndexOutOfBoundsException e) {
					// No valid information has been returned
					return null;
				}
				getCountyLocalityAndStreets(g, text);
			}
		}
		
		return g;
	}
	
	/**
	 * Parse search result text and extract relevant county, locality and
	 * street(s) information.
	 * 
	 * @param g GeoInfo object
	 * @param search result text
	 */
	private static void getCountyLocalityAndStreets(GeoInfo g, String text) {
		text = translateToEnglish(text);
		
		// Format is: Locality(County), Street
		// Street is optional
		Pattern p = Pattern.compile("(.+)\\((.+)\\),(.+)?");
		Matcher m = p.matcher(text);
		if (m.find()) {
			g.setLocality(m.group(1));
			g.setCounty(m.group(2));
			// If present and valid
			if (m.group(3) != null && m.group(3).length() > 1) {
				g.addStreet(m.group(3));
			}
		}
	}
	
	/**
	 * Translate the given text that might contain Romanian characters into
	 * an English only text.
	 * 
	 * @param text to be translated
	 * @return translated text
	 */
	private static String translateToEnglish(String text) {
	    try {
            text = text.replaceAll("\u0103", "a");
            text = text.replaceAll("\u00EE", "i");
            text = text.replaceAll("\u00E2", "a");
            text = text.replaceAll("\u015F", "s");
            text = text.replaceAll("\u0219", "s");
            text = text.replaceAll("\u021B", "t");
            text = text.replaceAll("\u0163", "t");
            
            text = text.replaceAll("\u0102", "A");
            text = text.replaceAll("\u00CE", "I");
            text = text.replaceAll("\u00C2", "A");
            text = text.replaceAll("\u0218", "S");
            text = text.replaceAll("\u015E", "S");
            text = text.replaceAll("\u021A", "T");
            text = text.replaceAll("\u0162", "T");
	    } catch (Exception e) {};
        return text;
	}
	
	/**
	 * Cleanup street name for polluting elements to make it usable for searching. 
	 * 
	 * @param name Dirty(db raw) street name
	 * @return clean street name
	 */
	private static String cleanupStreetName(String name) {
		int pos;
		
		for (String str : new String[]{" nr.", " bl."}) {
			pos = name.indexOf(str);
			if (pos != -1) {
				name = name.substring(0, pos);
			}
		}
		
		return name;
	}
	
	/**
	 * Wrapper for sleep().
	 * 
	 * @param millis to sleep
	 */
	public static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {}
	}
	
	/**
	 * When a driver exception is thrown while navigating on a web-site, we load opera:blank
	 * and wait a timeout, before continuing.
	 * 
	 * @param driver OperaDriver instance
	 * @param url to be loaded after the timeout has passed
	 */
	private static void handleNavigationException(OperaDriver driver, String url) {
		driver.navigate().to("opera:blank");
		sleep(EXCEPTION_SLEEP);
		driver.navigate().to(url);
	}
	
	/**
	 * Get an OperaDriver instance properly configured for our use cases.
	 * 
	 * @return OperaDriver instance
	 */
	private static OperaDriver getOperaDriver() {
		OperaDriver driver = new OperaDriver();
		
		// Use an implicit wait(i.e. poll the DOM for an amount of time when
		// searching for elements, if they aren't immediately available)
		driver.manage().timeouts().implicitlyWait(1, TimeUnit.SECONDS);
		
		// Clean the browser
		driver.utils().clearPrivateData(PrivateData.ALL);
		
		return driver;
	}
	
	/**
	 * Parse command line arguments and extract relevant data.
	 * 
	 * @param args Command line arguments
	 */
	private static void getCommandLineArguments(String[] args) {
		// Each option has a value
		if (args.length != 4) {
			LOGGER.severe("Lacking or incomplete command line arguments\n\nUsage:\n" +
					" java -jar zipcode-translator-v2.jar -i <input_file> -s <seconds_to_sleep>\n");
			System.exit(1);
		}
		
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option inputFileOption = parser.addStringOption('i', "inputfile");
		CmdLineParser.Option iterationSleepOption = parser.addStringOption('s', "iterationsleep");
		
		try {
			parser.parse(args);
		} catch (CmdLineParser.OptionException e) {
			LOGGER.severe("Exception while parsing command line arguments: " + e);
			System.exit(1);
		}
		
		inputFile = new File((String)parser.getOptionValue(inputFileOption));
		iterationSleep = 1000 * Long.parseLong((String)parser.getOptionValue(iterationSleepOption));
	}
}