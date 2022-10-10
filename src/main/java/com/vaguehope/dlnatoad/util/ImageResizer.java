package com.vaguehope.dlnatoad.util;

import static com.twelvemonkeys.imageio.util.IIOUtil.lookupProviderByName;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.FileImageOutputStream;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterators;

public class ImageResizer {

	private static final Object[] LOCK = new Object[0];
	private static final Logger LOG = LoggerFactory.getLogger(ImageResizer.class);

	private final File cacheDir;

	public ImageResizer(final File cacheDir) {
		this.cacheDir = cacheDir;
		forceReaderOrder();
		LOG.info("Supported formats: {}", Arrays.toString(ImageIO.getReaderFileSuffixes()));
	}

	// 12 Monkeys decoder is in installed cos it handles more file variants.
	// But the internal decoder is way faster, so always try that one first.
	private static void forceReaderOrder() {
		final IIORegistry iioRegistry = IIORegistry.getDefaultInstance();
		final Class<ImageReaderSpi> category = javax.imageio.spi.ImageReaderSpi.class;

		final ImageReaderSpi sunReader = lookupProviderByName(iioRegistry, "com.sun.imageio.plugins.jpeg.JPEGImageReaderSpi", ImageReaderSpi.class);
		final ImageReaderSpi monReader = lookupProviderByName(iioRegistry, "com.twelvemonkeys.imageio.plugins.jpeg.JPEGImageReaderSpi", ImageReaderSpi.class);

		if (sunReader == null || monReader == null) {
			LOG.warn("Did not find expected ImageReaderSpi classes to override order: sun={} 12mon={}", sunReader, monReader);
			LOG.info("Known ImageReaderSpi: {}", Iterators.toString(iioRegistry.getServiceProviders(category, true)));
			return;
		}

		iioRegistry.setOrdering(category, sunReader, monReader);
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
		final BufferedImage inImg = readImage(inF);

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

	private static BufferedImage readImage(final File file) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(file)) {
			final Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
			if (!readers.hasNext()) {
				throw new IOException("No reader for: " + file);
			}

			Exception lastException = null;
			while (readers.hasNext()) {
				final ImageReader reader = readers.next();
				try {
					final ImageReadParam param = reader.getDefaultReadParam();
					input.mark();
					reader.setInput(input, true, true);
					return reader.read(0, param);
				}
				catch (final Exception e) {
					input.reset();
					lastException = e;
					LOG.warn("Failed to decode \"{}\" with {}: {}", file.getAbsolutePath(), reader.getClass(), e.toString());
				}
				finally {
					reader.dispose();
				}
			}
			if (lastException != null) {
				if (lastException instanceof IOException) throw (IOException) lastException;
				throw new IOException(lastException);
			}
		}
		return null;
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
