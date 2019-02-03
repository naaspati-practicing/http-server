package sam.http.server.extra;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.Iterator;

import fi.iki.elonen.NanoHTTPD;
import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;

public class Page {
	public final String suffix;
	public final StringBuilder sb = new StringBuilder();
	public final int START;
	
	public Page(InputStream is) throws UnsupportedEncodingException, IOException {
		try(InputStreamReader isr = new InputStreamReader(is, "utf-8");
				BufferedReader reader = new BufferedReader(isr)) {

			int[] len = {-1};
			StringBuilder temp = null;
			String marker = "{{CONTENT}}";

			Iterator<String> iter = reader.lines().iterator();
			while (iter.hasNext()) {
				String s = iter.next();
				
				if(temp != null)
					temp.append(s).append('\n');
				else {
					int n = s.indexOf(marker); 
					if(n < 0)
						this.sb.append(s).append('\n');
					else {
						this.sb.append(s, 0, n);
						len[0] = this.sb.length();
						temp = new StringBuilder();
						temp.append(s, n + marker.length(), s.length());
					}
				}
				
			}

			suffix = temp.toString();
			START = len[0];
		}
	}
	@SuppressWarnings("rawtypes")
	public Page(Class cls) throws UnsupportedEncodingException, IOException {
		this(cls.getResourceAsStream(cls.getSimpleName()+".html"));
	}
	public Page reset() {
		sb.setLength(START);
		return this;
	}
	public Page append(String s) {
		sb.append(s);
		return this;
	}
	public Page finish() {
		sb.append(suffix);
		return this;
	}
	public Response response(Status status) {
		return NanoHTTPD.newFixedLengthResponse(status, Utils.TEXT_HTML, sb.toString());
	}
	@Override
	public String toString() {
		return sb.toString();
	}
}
