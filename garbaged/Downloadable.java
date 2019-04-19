package sam.server.file;

import java.io.Serializable;
import java.util.Objects;

class Downloadable implements Serializable {
	private static final long serialVersionUID = 1498411469891190491L;
	
	public final String identifier;
	public final String attr;
	public final String url;
	
	public Downloadable(String identifier, String attr, String url) {
		this.identifier = identifier;
		this.url = url;
		this.attr = attr;
	}

	@Override
	public int hashCode() {
		return Objects.hash(attr, identifier, url);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (!(obj instanceof Downloadable))
			return false;
		
		Downloadable other = (Downloadable) obj;
		return Objects.equals(attr, other.attr) && Objects.equals(identifier, other.identifier)
				&& Objects.equals(url, other.url);
	}
	
}
