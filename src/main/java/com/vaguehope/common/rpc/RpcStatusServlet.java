package com.vaguehope.common.rpc;

import java.io.PrintWriter;
import java.util.Map.Entry;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.common.collect.ImmutableTable;
import com.google.common.collect.ImmutableTable.Builder;
import com.vaguehope.common.rpc.RpcMetrics.ChannelState;
import com.vaguehope.common.rpc.RpcMetrics.EndpointRecorder;
import com.vaguehope.common.rpc.RpcMetrics.MethodMetrics;
import com.vaguehope.common.rpc.RpcMetrics.TimeSet;
import com.vaguehope.common.servlet.StatusPageServlet;

import io.grpc.Status;

public class RpcStatusServlet extends StatusPageServlet {

	public static final String CONTEXTPATH = "/rpc";
	private static final long serialVersionUID = -5847723732009179739L;

	@Override
	protected void generatePageBodyHtml(final HttpServletRequest req, final HttpServletResponse resp, final PrintWriter w) {
		if (RpcMetrics.serverMethodAndMetrics().size() > 0) {
			w.println("<h2>server requests</h2>");
			final Builder<String, String, String> srTable = ImmutableTable.builder();
			for (final Entry<String, MethodMetrics> mm : RpcMetrics.serverMethodAndMetrics()) {
				srTable.put(mm.getKey(), "active", String.valueOf(mm.getValue().activeRequests()));
				for (final Entry<Status.Code, TimeSet> sc : mm.getValue().statusAndCount()) {
					srTable.put(mm.getKey(), String.valueOf(sc.getKey()), timeSetHtml(sc.getValue()));
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
		final Builder<String, String, String> crTable = ImmutableTable.builder();
		for (final Entry<String, EndpointRecorder> cm : RpcMetrics.clientMetrics()) {
			for (final Entry<String, MethodMetrics> mm : cm.getValue().methodAndMetrics()) {
				final String rowKey = cm.getKey() + mm.getKey();
				crTable.put(rowKey, "active", String.valueOf(mm.getValue().activeRequests()));
				for (final Entry<Status.Code, TimeSet> sc : mm.getValue().statusAndCount()) {
					crTable.put(rowKey, String.valueOf(sc.getKey()), timeSetHtml(sc.getValue()));
				}
			}
		}
		tableToHtml(w, crTable.build());
	}

	private static String timeSetHtml(final TimeSet ts) {
		return ts.getFiveMin() + "<br>" + ts.getOneHour() + "<br>" + ts.getOneDay();
	}

}
