package com.vaguehope.common.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.Table;

@SuppressWarnings("serial")
public abstract class StatusPageServlet extends HttpServlet {

	public static final String BASIC_DARK_CSS = "body {"
			+ "  font-family: monospace;"
			+ "  background: #000;"
			+ "  color: #fff;"
			+ "}"
			+ "table, th, td {"
			+ "  border: 1px solid black;"
			+ "  border-collapse: collapse;"
			+ "  padding: 0.5em;"
			+ "  text-align: center;"
			+ "  border: #777 1px solid;"
			+ "}";
	private static final String PAGE_START = "<!DOCTYPE html><html>"
			+ "<head><style>" + BASIC_DARK_CSS + "</style></head>"
			+ "<body>";
	private static final String PAGE_END = "</body></html>";

	@Override
	@SuppressWarnings("resource")
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/html");
		final PrintWriter w = resp.getWriter();
		w.println(PAGE_START);
		generatePageBodyHtml(req, resp, w);
		w.println(PAGE_END);
	}

	protected abstract void generatePageBodyHtml(HttpServletRequest req, HttpServletResponse resp, PrintWriter w);

	public static <R, C, V> void tableToHtml(final PrintWriter w, final Table<R, C, V> table) {
		final Set<C> columns = table.columnKeySet();
		w.println("<table>");
		w.print("<tr><th></th>");
		for (final C c : columns) {
			w.print("<th>" + c + "</th>");
		}
		w.println("</tr>");
		for (final Entry<R, Map<C, V>> re : table.rowMap().entrySet()) {
			w.print("<tr><td>" + re.getKey() + "</td>");
			for (final C c : columns) {
				final V val = re.getValue().get(c);
				if (val == null) {
					w.print("<td></td>");
				}
				else {
					w.print("<td>" + val + "</td>");
				}
			}
			w.println("</tr>");
		}
		w.println("</table>");
	}

}
