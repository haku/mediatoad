package com.vaguehope.dlnatoad.util;

import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.InOrder;

import com.vaguehope.dlnatoad.util.TreeWalker.Hiker;

public class TreeWalkerTest {

	@Rule public TemporaryFolder tmp = new TemporaryFolder();

	@Test
	public void itWalksTree () throws Exception {
		File file1 = this.tmp.newFile("file_1");
		File file2 = this.tmp.newFile("file_2");
		File dir1 = this.tmp.newFolder("dir_1");
		File file3 = new File(dir1, "file_3");
		file3.createNewFile();
		File file4 = new File(dir1, "file_4");
		file4.createNewFile();

		FileFilter fileFilter = mock(FileFilter.class);
		when(fileFilter.accept(isA(File.class))).thenReturn(true);

		Hiker hiker = mock(Hiker.class);
		List<File> roots = new ArrayList<File>();
		roots.add(this.tmp.getRoot());
		new TreeWalker(roots, fileFilter, hiker).walk();

		InOrder o = inOrder(hiker);
		o.verify(hiker).onDirWithFiles(this.tmp.getRoot(), Arrays.asList(file1, file2));
		o.verify(hiker).onDirWithFiles(dir1, Arrays.asList(file3, file4));
		o.verifyNoMoreInteractions();
	}

}
