package sam.http.server.api;

import java.io.IOException;

import fi.iki.elonen.NanoHTTPD.Response;
import fi.iki.elonen.NanoHTTPD.Response.Status;
import sam.myutils.MyUtilsException;

public final class ServerException {
	private static final Page page;
	
	private ServerException() { }
	
	static {
		try {
			page = new Page(ServerException.class);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public static Response create(Status status, String msg) {
		return create(status, msg, null);	
	}
	
	private static final String DIV_CLOSE = "</div>"; 
	
	public static Response create(Status status, String msg, Throwable ex) {
		synchronized (page) {
			page.reset();
			page.sb.append("<h1 class=\"error_code\">").append(status.getRequestStatus()).append("</h1>");
			if(msg != null)
				page.append("<p class=\"error_msg\">").append(msg).append("<p>");

			if(ex != null) {
				page.append(DIV_CLOSE).append("\n<pre id=\"stacktrace\">");
				MyUtilsException.append(page.sb, ex, true);
				page
				.sb.append("</pre>\n")
				.append(page.suffix, page.suffix.indexOf(DIV_CLOSE) + DIV_CLOSE.length(), page.suffix.length());
			} else {
				page.finish();
			}
			return page.response(Status.ACCEPTED);
		}
	}
}
