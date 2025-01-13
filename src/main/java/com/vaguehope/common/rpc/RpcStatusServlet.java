package com.vaguehope.common.rpc;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ImmutableTable.Builder;
import com.google.common.collect.Table;
import com.vaguehope.common.rpc.RpcMetrics.ChannelState;
import com.vaguehope.common.rpc.RpcMetrics.EndpointRecorder;
import com.vaguehope.common.rpc.RpcMetrics.MethodMetrics;

import io.grpc.Status;

public class RpcStatusServlet extends HttpServlet {

	public static final String CONTEXTPATH = "/rpc";
	private static final long serialVersionUID = -5847723732009179739L;

	@SuppressWarnings("resource")
	@Override
	protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws ServletException, IOException {
		resp.setContentType("text/html");
		final PrintWriter w = resp.getWriter();
		w.println("<!DOCTYPE html><html>"
				+ "<head><style>"
				+ "table, th, td {"
				+ "  border: 1px solid black;"
				+ "  border-collapse: collapse;"
				+ "  padding: 0.5em;"
				+ "}"
				+ "</style></head>"
				+ "<body>");
		w.println("<h1>rpc status</h1>");

		if (RpcMetrics.serverMethodAndMetrics().size() > 0) {
			w.println("<h2>server requests</h2>");
			final Builder<String, Status.Code, String> srTable = ImmutableTable.builder();
			for (final Entry<String, MethodMetrics> mm : RpcMetrics.serverMethodAndMetrics()) {
				for (final Entry<Status.Code, AtomicInteger> sc : mm.getValue().statusAndCount()) {
					srTable.put(mm.getKey(), sc.getKey(), sc.getValue().toString());
				}
			}
			tableToHtml(w, srTable.build());
		}

		w.println("<h2>client channels</h2>");
		w.println("<table><tr><th>connection</th><th>state</th></tr>");
		for (final ChannelState c : RpcMetrics.channelStates()) {
			w.println(String.format("<tr><td>%s</td><td>%s</td></tr>", c.getName(), c.getState()));
		}
		w.println("</table>");

		w.println("<h2>client requests</h2>");
		final Builder<String, Status.Code, String> crTable = ImmutableTable.builder();
		for (final Entry<String, EndpointRecorder> cm : RpcMetrics.clientMetrics()) {
			for (final Entry<String, MethodMetrics> mm : cm.getValue().methodAndMetrics()) {
				final String rowKey = cm.getKey() + mm.getKey();
				for (final Entry<Status.Code, AtomicInteger> sc : mm.getValue().statusAndCount()) {
					crTable.put(rowKey, sc.getKey(), sc.getValue().toString());
				}
			}
		}
		tableToHtml(w, crTable.build());

		w.println("</body></html>");
	}

	private static <R, C, V> void tableToHtml(final PrintWriter w, final Table<R, C, V> table) {
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
				V val = re.getValue().get(c);
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
