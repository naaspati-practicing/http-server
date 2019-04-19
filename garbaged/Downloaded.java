package sam.server.file;

import java.io.Serializable;
import java.util.Map;

class Downloaded implements Serializable {
	private static final long serialVersionUID = 4909460094639292997L;
	
	public static final String NAME = "name";
	public static final String PATH = "path";
	public static final String IDENTIFIER = "identifier";
	public static final String ATTR = "attr";
	public static final String URL = "url";

	public final String name;
	public String path;
	public final Downloadable downloadable;

	public Downloaded(Downloadable downloadable, String name, String path) {
		this.name = name;
		this.path = path;
		this.downloadable = downloadable;
	}
	public Downloaded(String name,  Map<String, String> map) {
		this.name = name;
		this.path = map.get(PATH);
		this.downloadable = new Downloadable(
				map.get(IDENTIFIER), 
				map.get(ATTR), 
				map.get(URL)
				);
	}
	public String getName() {
		return name;
	}
	public String getPath() {
		return path;
	}
	public Downloadable getImg() {
		return downloadable;
	}
	public void setPath(String path) {
		this.path = path;
	}
	public void toMap(Map<String, String> map) {
		map.put(NAME, name);
		map.put(PATH, path);
		
		map.put(IDENTIFIER, downloadable.identifier);
		map.put(ATTR, downloadable.attr);
		map.put(URL, downloadable.url);
	}
}
