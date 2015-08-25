import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Logger;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

public class InputData {
	
	private static final Logger LOGGER = Logger.getLogger(InputData.class.getName());

	private File file;

	
	public InputData(File file) {
		this.file = file;
	}

	/**
	 * Parse input file and extract all valid ZIP codes.
	 * 
	 * @return List of valid ZIP codes
	 * @throws IOException if any error is encountered during parsing of the input file
	 */
	public List<String> getZipCodes() throws IOException {		
		List<String> zipCodes = new ArrayList<String>();
		
		for (String line : Files.readLines(file, Charsets.UTF_8)) {
			StringTokenizer st = new StringTokenizer(line, " ,.;-\t");
			while (st.hasMoreTokens()) {
				String elem = st.nextToken();
				try {
					// Check ZIP code validity
					Long.parseLong(elem);
					zipCodes.add(elem);
				} catch (NumberFormatException e) {
					LOGGER.warning("Invalid zip code found: " + elem);
				}
			}
		}
		
		if (zipCodes.size() == 0) {
			throw new IOException("No valid zip codes were found in the input file");
		}
		
		LOGGER.info("Found " + zipCodes.size() + " zip codes.");
		
		if (isExistingWork(zipCodes)) {
			LOGGER.info("Found processed zip codes. Will check the remaining " + zipCodes.size() + " zip codes.");
		}

		return zipCodes;
	}
	
	/**
	 * Check if there's an existing results file and if there's been already some
	 * work done. If true, eliminate those ZIP codes from the new run.
	 * 
	 * @param zipCodes list of ZIP codes to be processed
	 * @return true if there was some work already done
	 * @throws IOException if results file doesn't exist and we try to use it
	 */
	private boolean isExistingWork(List<String> zipCodes) throws IOException {
		boolean ret = false;
		
		for (File file : ImmutableList.of(ResultWriter.results, ResultWriter.invalids)) {
			if (file.exists()) {
				int i = 0;
				for (String line : Files.readLines(file, Charsets.UTF_8)) {
					// Skip first 2 lines(header and empty line)
					if (i++ > 1) {					
						StringTokenizer st = new StringTokenizer(line, ";");
						if (st.hasMoreElements()) {
							String zipCode = st.nextToken();
							if (zipCodes.contains(zipCode)) {
								zipCodes.remove(zipCode);
								ret = true;
							}
						}
					}
				}
			}
		}

		return ret;
	}
}
