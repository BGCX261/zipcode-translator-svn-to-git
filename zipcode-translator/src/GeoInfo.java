import java.util.ArrayList;
import java.util.List;

public class GeoInfo {
	
	private String county;

	private String locality;
	
	private List<String> streets;
	
	private String latitude;
	
	private String longitude;
	
	
	public GeoInfo() {
		streets = null;
		latitude = null;
		longitude = null;
	};
	
	/**
	 * Get latitude.
	 * 
	 * @return latitude string
	 */
	public String getLatitude() {
		return latitude;
	}
	
	/**
	 * Set latitude.
	 * 
	 * @param latitude string
	 */
	public void setLatitude(String latitude) {
		this.latitude = latitude;
	}
	
	/**
	 * Get longitude.
	 * 
	 * @return longitude string
	 */
	public String getLongitude() {
		return longitude;
	}
	
	/**
	 * Set longitude.
	 * 
	 * @param longitude string
	 */
	public void setLongitude(String longitude) {
		this.longitude = longitude;
	}
	
	/**
	 * Get county.
	 * 
	 * @return county name
	 */
	public String getCounty() {
		return county;
	}
	
	/**
	 * Set county.
	 * 
	 * @param county name
	 */
	public void setCounty(String county) {
		this.county = county;
	}
	
	/**
	 * Get locality.
	 * 
	 * @return locality name
	 */
	public String getLocality() {
		return locality;
	}
	
	/**
	 * Set locality.
	 * 
	 * @param locality name
	 */
	public void setLocality(String locality) {
		this.locality = locality;
	}
	
	/**
	 * Get list of streets.
	 * 
	 * @return list of streets
	 */
	public List<String> getStreets() {
		return streets;
	}
	
	/**
	 * Set list of streets.
	 * 
	 * @param list of streets
	 */
	public void setStreets(List<String> streets) {
		this.streets = streets;
	}
	
	/**
	 * Add street to the list of streets.
	 * 
	 * @param street name
	 */
	public void addStreet(String street) {
		if (streets == null) {
			streets = new ArrayList<String>();
		}
		streets.add(street);
	}
	
	/**
	 * Ascertains if this GeoInfo object has coordinates set.
	 * 
	 * @return true if coordinates are set
	 */
	public boolean hasCoordinates() {
		return latitude != null && longitude != null;
	}
}
