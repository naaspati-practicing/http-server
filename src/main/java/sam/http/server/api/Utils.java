package sam.http.server.api;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.ServiceLoader;
import java.util.function.Function;

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
	
	public static void setLogger(ServerLogger logger) {
		if(logger != null)
			throw new IllegalStateException("logger already set");
		
		Utils.logger = logger;
	}
	
	private Utils() { }
	
	public static <E> E[] serviceLoaded(Class<E> cls) {
		return serviceLoaded(cls,cls, s -> s);
	}
	
	@SuppressWarnings("unchecked")
	public static <E, F> F[] serviceLoaded(Class<E> serviceClass, Class<F> targetClass, Function<E, F> mapper) {
		ArrayList<F> handler = new ArrayList<>();

		ServiceLoader.load(serviceClass)
		.iterator().forEachRemaining(s -> handler.add(mapper.apply(s)));

		if(handler.isEmpty())
			return null;
		else
			return handler.toArray((F[]) Array.newInstance(targetClass, handler.size()));
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
}
