package com.vaguehope.dlnatoad.util;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;

public class ImageResizer {

	private static final Object[] LOCK = new Object[0];

	private final File cacheDir;

	public ImageResizer(final File cacheDir) {
		this.cacheDir = cacheDir;
	}

	/**
	 * @param quality Max 1.0.
	 */
	public File resizeFile (final File inF, final int size, final float quality) throws IOException {
		if (!inF.exists()) throw new IllegalArgumentException("File does not exist: " + inF.getAbsolutePath());
		if (size < 16 || size > 1000) throw new IllegalArgumentException("Invalid size: " + size);

		final File outF = new File(this.cacheDir, HashHelper.md5(inF.getAbsolutePath()).toString(16) + "_" + size + ".jpg");
		if (outF.exists() && outF.lastModified() > inF.lastModified()) return outF;

		// TODO do something better than this nasty rate-limiting hack.
		synchronized (LOCK) {
			return scaleImageToFile(inF, size, quality, outF);
		}
	}

	private static File scaleImageToFile (final File inF, final int size, final float quality, final File outF) throws IOException {
		final BufferedImage inImg = ImageIO.read(inF);

		if (inImg.getWidth() < 1 || inImg.getHeight() < 1) throw new IllegalArgumentException("Image too small: " + inF.getAbsolutePath());

		final int width, height;
		if (inImg.getWidth() == inImg.getHeight()) {
			width = size;
			height = size;
		}
		else if (inImg.getWidth() > inImg.getHeight()) {
			width = size;
			height = (int) (inImg.getHeight() * size / (double) inImg.getWidth());
		}
		else {
			width = (int) (inImg.getWidth() * size / (double) inImg.getHeight());
			height = size;
		}

		final BufferedImage outImg = scaleImage(inImg, width, height);
		writeImageViaTmpFile(outImg, quality, outF);
		return outF;
	}

	private static BufferedImage scaleImage (final BufferedImage inImg, final int width, final int height) {
		final BufferedImage outImg = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		final Graphics2D g = outImg.createGraphics();
		try {
			g.setComposite(AlphaComposite.Src);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.drawImage(inImg, 0, 0, width, height, Color.BLACK, null);
		}
		finally {
			g.dispose();
		}
		return outImg;
	}

	private static void writeImageViaTmpFile (final BufferedImage outImg, final float quality, final File f) throws IOException {
		final File ftmp = new File(f.getAbsolutePath() + ".tmp");
		try {
			writeImageToFile(outImg, quality, ftmp);
			if (!ftmp.renameTo(f)) throw new IOException("Failed to rename '" + ftmp.getAbsolutePath() + "' to '" + f.getAbsolutePath() + "'.");
		}
		finally {
			if (ftmp.exists()) ftmp.delete();
		}
	}

	private static void writeImageToFile (final BufferedImage outImg, final float quality, final File f) throws IOException {
		final ImageWriter jpgWriter = ImageIO.getImageWritersByFormatName("jpg").next();
		try {
			final ImageWriteParam jpegParams = jpgWriter.getDefaultWriteParam();
			jpegParams.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			jpegParams.setCompressionQuality(quality);

			final FileImageOutputStream ios = new FileImageOutputStream(f);
			try {
				jpgWriter.setOutput(ios);
				jpgWriter.write(null, new IIOImage(outImg, null, null), jpegParams);
			}
			finally {
				ios.close();
			}
		}
		finally {
			jpgWriter.dispose();
		}
	}

}
