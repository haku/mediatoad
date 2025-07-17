package com.vaguehope.dlnatoad;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.vaguehope.dlnatoad.Args.ArgsException;
import com.vaguehope.dlnatoad.media.ContentServingHistory;
import com.vaguehope.dlnatoad.media.ContentTree;
import com.vaguehope.dlnatoad.ui.ServletCommon;

public class FakeServletCommon {

	public static ServletCommon make() throws ArgsException {
		final ContentTree contentTree = mock(ContentTree.class);
		final Args args = mock(Args.class);
		return new ServletCommon(contentTree, null, null, false, args);
	}

	public static ServletCommon make(final ContentTree contentTree) throws ArgsException {
		final Args args = mock(Args.class);
		return new ServletCommon(contentTree, null, null, false, args);
	}

	public static ServletCommon make(final ContentTree contentTree, final String hostName, final ContentServingHistory contentServingHistory) throws ArgsException {
		final Args args = mock(Args.class);
		return new ServletCommon(contentTree, hostName, contentServingHistory, false, args);
	}

	public static ServletCommon makeWithDbEnabled(final ContentTree contentTree) throws ArgsException {
		final Args args = mock(Args.class);
		return new ServletCommon(contentTree, null, null, true, args);
	}

	public static ServletCommon makeWithPathPrefix(final String httpPathPrefix) throws ArgsException {
		final ContentTree contentTree = mock(ContentTree.class);
		final Args args = mock(Args.class);
		when(args.getHttpPathPrefix()).thenReturn(httpPathPrefix);
		return new ServletCommon(contentTree, null, null, false, args);
	}

}
