package sam.server.file;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

final class ReplaceUrls {
	
	private ReplaceUrls() { }

	private static final AtomicInteger counter = new AtomicInteger(0); 
	private static final String ROOT = BaseDocRoot.RESOURCE_URI+"/";

	public static String parse(InputStream is, BiConsumer<Integer, Downloadable> onFound) throws IOException {
		Document doc = Jsoup.parse(is, "utf-8", "baseUri");

		doc.getElementsByTag("img")
		.forEach(e -> {
			String src = e.attr("src");
			if(src == null || !src.startsWith("http"))
				return;
			
			String selector = e.cssSelector();
			int id = counter.incrementAndGet();
			e.attr("src", ROOT+id);
			// TODO plan to replace all urls with external resource
			onFound.accept(id, new Downloadable(selector, "src", src));
		});

		return doc.toString();
	}

}
