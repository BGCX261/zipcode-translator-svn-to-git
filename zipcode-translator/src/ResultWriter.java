import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class ResultWriter {
	
	private static final Logger LOGGER = Logger.getLogger(ResultWriter.class.getName());

	/**
	 * Header of the result files.
	 */
	private static final String HEADER = "Format: ZIP CODE; STREET; LOCALITY; COUNTY; COORDINATES(LAT, LON)\n\n";
	
	/**
	 * File with the streets for which we found at least a valid coordinate.
	 */
	public static final File results;
	
	/**
	 * File with the GeoInfo for which we couldn't find the coordinates.
	 */
	public static final File errors;
	
	/**
	 * File with the invalid(that don't exist) ZIP codes.
	 */
	public static final File invalids;
	
	/**
	 * Counter for number of valid results written.
	 */
	private int resultsCounter = 0;
	
	/**
	 * Counter for the number of errors written.
	 */
	private int errorsCounter = 0;
	
	/**
	 * Counter for the number of invalids written.
	 */
	private int invalidsCounter = 0;
	
	
	static {
		File dir = new File(System.getProperty("user.home"), "zipcode-translator");
		if (!dir.exists()) {
			dir.mkdir();
		}
		
		results = new File(dir, "results.txt");
		errors = new File(dir, "errors.txt");
		invalids = new File(dir, "invalids.txt");
		if (errors.exists()) {
			errors.delete();
		}
	}
	
	public ResultWriter() throws IOException {
		for (File file : ImmutableList.of(results, errors)) {
			if (!file.exists()) {
				Files.write(HEADER, file, Charsets.UTF_8);
			}
		}
		if (!invalids.exists()) {
			Files.write("Format: ZIP CODE\n\n", invalids, Charsets.UTF_8);
		}
	}

	/**
	 * Write results to file.
	 * 
	 * @param zipCode value
	 * @param g Geographical information object
	 * @param street name(can be null)
	 * @throws IOException if something went wrong during write()
	 */
	public void writeResult(String zipCode, GeoInfo g, String street) throws IOException {
		resultsCounter++;
		write(results, zipCode, g, street);
	}
	
	/**
	 * Write errors to file.
	 * 
	 * @param zipCode value
	 * @param g Geographical information object
	 * @param street name(can be null)
	 * @throws IOException if something went wrong during write()
	 */
	public void writeError(String zipCode, GeoInfo g, String street) throws IOException {
		errorsCounter++;
		write(errors, zipCode, g, street);
	}
	
	/**
	 * Write invalid ZIP code to file.
	 * 
	 * @param zipCode that is invalid
	 * @throws IOException if something went wrong during append()
	 */
	public void writeInvalid(String zipCode) throws IOException {
		invalidsCounter++;
		Files.append(zipCode + ";\n", invalids, Charsets.UTF_8);
	}
	
	/**
	 * Write(in append mode) the message line into the provided file.
	 * 
	 * @param file File where to write
	 * @param zipCode value
	 * @param g Geographical information object
	 * @param street name(can be null)
	 * @throws IOException if something went wrong during append()
	 */
	private void write(File file, String zipCode, GeoInfo g, String street) throws IOException {
		Files.append(getMessage(zipCode, g, street), file, Charsets.UTF_8);
	}
	
	/**
	 * Get message to be written in a results file.
	 * 
	 * @param zipCode value
	 * @param g Geographical information object
	 * @param street name(can be null)
	 * @return message
	 */
	private String getMessage(String zipCode, GeoInfo g, String street) {
		if (g != null) {
			String str = String.format("%s; %s; %s; %s;", zipCode, street == null ? "-" : street,
					g.getLocality(), g.getCounty());
			if (g.hasCoordinates()) {
				str += String.format(" %s, %s", g.getLatitude(), g.getLongitude());
			}
			return str + "\n";
		} else {
			return zipCode + "\n";
		}
	}
	
	/**
	 * Print to the logger a summary of the actions that happened inside this result writer.
	 */
	public void showSummary() {
		LOGGER.info("Summary:\n" +
			String.format(" - %d results  written to: %s\n", resultsCounter, results.getAbsolutePath()) +
			String.format(" - %d invalids written to: %s\n", invalidsCounter, invalids.getAbsolutePath()) +
			String.format(" - %d errors   written to: %s\n", errorsCounter, errors.getAbsolutePath()));
	}
}
