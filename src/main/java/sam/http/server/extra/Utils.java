package sam.http.server.extra;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import fi.iki.elonen.NanoHTTPD;

public final class Utils {
	private static ServerLogger logger;

	public static final String TEXT_PLAIN = "text/plain"; 
	public static final String TEXT_HTML = "text/html";
	public static final String TEXT_CSS = "text/css";
	public static final String TEXT_JAVASCRIPT = "text/javascript";
	public static final String APPLICATION_JSON = "application/json";
	public static final String IMAGE_JPEG = "image/jpeg";
	public static final String IMAGE_PNG = "image/png";

	private static final Object SB_LOCK = new Object();
	private static final StringBuilder sb = new StringBuilder();
	private static final char[] chars = new char[512];

	private Utils() { }

	public static String readString(InputStream is, int charsToRead) throws IOException {
		synchronized (SB_LOCK) {
			InputStreamReader isr = new InputStreamReader(is);

			sb.setLength(0);
			while(sb.length() < charsToRead) {
				int n = isr.read(chars);
				if(n == -1)
					throw new IOException("expected length: "+charsToRead+", but found: "+sb.length());

				sb.append(chars, 0, n);
			}
			return sb.toString();
		}
	}
	public static String readStringFully(InputStream is) throws IOException {
		synchronized (SB_LOCK) {
			try(InputStream is2 = is;
					InputStreamReader isr = new InputStreamReader(is); ) {
				int n = 0;
				sb.setLength(0);

				while((n = isr.read(chars)) != -1) 
					sb.append(chars, 0, n);

				return sb.toString();	
			}

		}
	}

	public static String getMimeTypeForFile(String name) {
		int n = name.lastIndexOf('.');

		if(n < 0)
			return TEXT_PLAIN;

		String ext = name.substring(n+1).toLowerCase();

		switch (ext) {
			case "html":  return TEXT_HTML;
			case "htm":   return TEXT_HTML;

			case "css":   return TEXT_CSS;

			case "js":    return TEXT_JAVASCRIPT;
			case "json":  return APPLICATION_JSON;

			case "jpg":   return IMAGE_JPEG;
			case "jpeg":  return IMAGE_JPEG;
			case "jpe":   return IMAGE_JPEG;

			case "png":   return IMAGE_PNG;

			default:
				String s = NanoHTTPD.getMimeTypeForFile(ext);
				if(logger != null)
					logger.fine(() -> "MimeType.get(\""+ext+"\") = \""+s+"\"");
				return s;
		}
	}

	public static boolean alwaysTrue() {
		return true;
	}
	
	public static long pipe(InputStream is, OutputStream os, byte[] buffer) throws IOException {
		long tn = 0;
		int n = 0;
		while((n = is.read(buffer)) != -1) {
			tn += n;
			os.write(buffer, 0, n);
		}
		return tn;
			
	}
	public static long pipe(Path p, OutputStream os, byte[] buffer) throws IOException {
		try(InputStream is = Files.newInputStream(p, StandardOpenOption.READ)) {
			return  pipe(is, os, buffer);
		}
	}
}
