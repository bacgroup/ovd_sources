/* Orders.java
 * Component: ProperJavaRDP
 * 
 * Revision: $Revision: 1.1.1.1 $
 * Author: $Author: suvarov $
 * Author: David LECHEVALIER <david@ulteo.com> 2011
 * Date: $Date: 2007/03/08 00:26:31 $
 *
 * Copyright (c) 2005 Propero Limited
 * Copyright (C) 2011-2012 Ulteo SAS
 * http://www.ulteo.com
 * Author Thomas MOUTON <thomas@ulteo.com> 2012
 * Author David LECHEVALIER <david@ulteo.com> 2012
 * Author Vincent ROULLIER <v.roullier@ulteo.com> 2013
 *
 * Purpose: Encapsulates an RDP order
 */
package net.propero.rdp;

import org.apache.log4j.Logger;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.propero.rdp.orders.*;

public class Orders {
    static Logger logger = Logger.getLogger(Orders.class);

    private OrderState os = null;

    private RdesktopCanvas surface = null;
    
    private Options opt = null;
    private Common common = null;
    
    private boolean frameMarkerIsSet = false;

    /* RDP_BMPCACHE2_ORDER */
    private static final int ID_MASK = 0x0007;
    private static final int MODE_MASK = 0x0038;
    private static final int SQUARE = 0x0080;
    private static final int PERSIST = 0x0100;
    @SuppressWarnings("unused")
    private static final int FLAG_51_UNKNOWN = 0x0800;
    private static final int MODE_SHIFT = 3;
    private static final int LONG_FORMAT = 0x80;
    private static final int BUFSIZE_MASK = 0x3FFF; /* or 0x1FFF? */
    private static final int RDP_ORDER_STANDARD = 0x01;
    private static final int RDP_ORDER_SECONDARY = 0x02;
    private static final int RDP_ORDER_BOUNDS = 0x04;
    private static final int RDP_ORDER_CHANGE = 0x08;
    private static final int RDP_ORDER_DELTA = 0x10;
    private static final int RDP_ORDER_LASTBOUNDS = 0x20;
    private static final int RDP_ORDER_SMALL = 0x40;
    private static final int RDP_ORDER_TINY = 0x80;

    /* standard order types */
    private static final int RDP_ORDER_DESTBLT = 0;
    private static final int RDP_ORDER_PATBLT = 1;
    private static final int RDP_ORDER_SCREENBLT = 2;
    private static final int RDP_ORDER_LINE = 9;
    private static final int RDP_ORDER_RECT = 10;
    private static final int RDP_ORDER_DESKSAVE = 11;
    private static final int RDP_ORDER_MEMBLT = 13;
    private static final int RDP_ORDER_TRIBLT = 14;
    private static final int RDP_ORDER_POLYLINE = 22;
    private static final int RDP_ORDER_TEXT2 = 27;
    private int rect_colour = 0x0;

    /* secondary order types */
    private static final int RDP_ORDER_RAW_BMPCACHE = 0;
    private static final int RDP_ORDER_COLCACHE = 1;
    private static final int RDP_ORDER_BMPCACHE = 2;
    private static final int RDP_ORDER_FONTCACHE = 3;
    private static final int RDP_ORDER_RAW_BMPCACHE2 = 4;
    private static final int RDP_ORDER_BMPCACHE2 = 5;
    private static final int RDP_ORDER_JPEGCACHE = 99;

    /* alternate order types (GDI+) */
    private static final int RDP_ORDER_TS_ALTSEC_SWITCH_SURFACE = 0x0;
    private static final int RDP_ORDER_TS_ALTSEC_CREATE_OFFSCR_BITMAP = 0x1;
    
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_STREAM_BITMAP_FIRST = 0x02;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_STREAM_BITMAP_NEXT = 0x03;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_CREATE_NINEGRID_BITMAP = 0x04;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_FIRST = 0x05;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_NEXT = 0x06;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_END = 0x07;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_CACHE_FIRST = 0x08;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_CACHE_NEXT = 0x09;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_GDIP_CACHE_END = 0x0A;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_WINDOW= 0x0B;
	@SuppressWarnings("unused")
	private static final int RDP_ORDER_TS_ALTSEC_COMPDESK_FIRST = 0x0C;
	private static final int RDP_ORDER_TS_ALTSEC_FRAME_MARKER = 0x0D;
	
	private static final int TS_FRAME_START = 0x00000000;
	private static final int TS_FRAME_END = 0x00000001;

    @SuppressWarnings("unused")
    private static final int MIX_TRANSPARENT = 0;
    private static final int MIX_OPAQUE = 1;
    private static final int TEXT2_VERTICAL = 0x04;
    private static final int TEXT2_IMPLICIT_X = 0x20;

    public Orders(Options opt_, Common common_) {
    	this.opt = opt_;
	this.common = common_;
        os = new OrderState();
    }

    public void resetOrderState() {
        this.os.reset();
        os.setOrderType(RDP_ORDER_PATBLT);
        return;
    }

    private int inPresent(RdpPacket_Localised data, int flags, int size) {
        int present = 0;
        int bits = 0;
        int i = 0;

        if ((flags & RDP_ORDER_SMALL) != 0) {
            size--;
        }

        if ((flags & RDP_ORDER_TINY) != 0) {

            if (size < 2) {
                size = 0;
            } else {
                size -= 2;
            }
        }

        for (i = 0; i < size; i++) {
            bits = data.get8();
            present |= (bits << (i * 8));
        }
        return present;
    }

    /**
     * Process a set of orders sent by the server
     * @param data Packet packet containing orders
     * @param next_packet Offset of end of this packet (start of next)
     * @param n_orders Number of orders sent in this packet
     * @throws OrderException
     * @throws RdesktopException
     */
    public void processOrders(RdpPacket_Localised data, int next_packet,
            int n_orders) throws OrderException, RdesktopException {

        int present = 0;
        // int n_orders = 0;
        int order_flags = 0, order_type = 0;
        int size = 0, processed = 0;
        boolean delta;

        while (processed < n_orders) {

            order_flags = data.get8();

            if ((order_flags & RDP_ORDER_STANDARD) == 0) {
                this.processAlternateOrders(data, order_flags);
            }

            else if ((order_flags & RDP_ORDER_SECONDARY) != 0) {
                this.processSecondaryOrders(data);
            } else {

                if ((order_flags & RDP_ORDER_CHANGE) != 0) {
                    os.setOrderType(data.get8());
                }

                switch (os.getOrderType()) {
                case RDP_ORDER_TRIBLT:
                case RDP_ORDER_TEXT2:
                    size = 3;
                    break;

                case RDP_ORDER_PATBLT:
                case RDP_ORDER_MEMBLT:
                case RDP_ORDER_LINE:
                    size = 2;
                    break;

                default:
                    size = 1;
                }

                present = this.inPresent(data, order_flags, size);

                if ((order_flags & RDP_ORDER_BOUNDS) != 0) {

                    if ((order_flags & RDP_ORDER_LASTBOUNDS) == 0) {
                        this.parseBounds(data, os.getBounds());
                    }

                    surface.setClip(os.getBounds());
                }

                delta = ((order_flags & RDP_ORDER_DELTA) != 0);

                switch (os.getOrderType()) {
                case RDP_ORDER_DESTBLT:
                    logger.debug("DestBlt Order");
                    this.processDestBlt(data, os.getDestBlt(), present, delta);
                    break;

                case RDP_ORDER_PATBLT:
                    logger.debug("PatBlt Order");
                    this.processPatBlt(data, os.getPatBlt(), present, delta);
                    break;

                case RDP_ORDER_SCREENBLT:
                    logger.debug("ScreenBlt Order");
                    this.processScreenBlt(data, os.getScreenBlt(), present,
                            delta);
                    break;

                case RDP_ORDER_LINE:
                    logger.debug("Line Order");
                    this.processLine(data, os.getLine(), present, delta);
                    break;

                case RDP_ORDER_RECT:
                    logger.debug("Rectangle Order");
                    this.processRectangle(data, os.getRectangle(), present,
                            delta);
                    break;

                case RDP_ORDER_DESKSAVE:
                    logger.debug("Desksave!");
                    this
                            .processDeskSave(data, os.getDeskSave(), present,
                                    delta);
                    break;

                case RDP_ORDER_MEMBLT:
                    logger.debug("MemBlt Order");
                    this.processMemBlt(data, os.getMemBlt(), present, delta);
                    break;

                case RDP_ORDER_TRIBLT:
                    logger.debug("TriBlt Order");
                    this.processTriBlt(data, os.getTriBlt(), present, delta);
                    break;

                case RDP_ORDER_POLYLINE:
                    logger.debug("Polyline Order");
                    this
                            .processPolyLine(data, os.getPolyLine(), present,
                                    delta);
                    break;

                case RDP_ORDER_TEXT2:
                    logger.debug("Text2 Order");
                    this.processText2(data, os.getText2(), present, delta);
                    break;

                default:
                    logger.warn("Unimplemented Order type " + order_type);
                    return;
                }

                if ((order_flags & RDP_ORDER_BOUNDS) != 0) {
                    surface.resetClip();
                    logger.debug("Reset clip");
                }
            }

            processed++;
        }

	if (! this.common.rdp.useFrameMarker || ! this.frameMarkerIsSet) {
		this.surface.repaint_order();
	}
	
        if (! this.opt.bitmap_compression && (data.getPosition() != next_packet)) {
            throw new OrderException("End not reached!");
        }
    }

    private int ROP2_S(int rop3) {
        return (rop3 & 0x0f);
    }

    private int ROP2_P(int rop3) {
        return ((rop3 & 0x3) | ((rop3 & 0x30) >> 2));
    }

    /**
     * Register an RdesktopCanvas with this Orders object.
     * This surface is where all drawing orders will be carried
     * out.
     * @param surface Surface to register
     */
    public void registerDrawingSurface(RdesktopCanvas surface) {
        this.surface = surface;
        surface.registerCache(this.common.cache);
    }
    
    private void processSwitchSurface(RdpPacket_Localised data) throws RdesktopException {
    	int cache_idx;

    	cache_idx = data.getLittleEndian16();

    	if (cache_idx < 0) {
    		this.surface.backstore.setCurrent(null);
    		return;
    	}
    	Bitmap bitmap = this.common.cache.getBitmap(255, cache_idx);
    	this.surface.backstore.setCurrent(bitmap);
    }

    private void processCreateOffscrBitmap(RdpPacket_Localised data) throws RdesktopException {
    	int cache_idx, cx, cy, count, idx;
    	
    	cache_idx = data.getLittleEndian16();
    	cx = data.getLittleEndian16();
    	cy = data.getLittleEndian16();

    	// Check if there is deletion list
    	if ((cache_idx & 0x8000) > 0) {
    		count = data.getLittleEndian16();
    		
    		for (int i = 0; i < count; i++) {
    			idx = data.getLittleEndian16();
    			this.common.cache.putBitmap(255, idx, null, 0);
    		}
    	}
    	cache_idx &= 0xFFFF;
    	cache_idx &= ~0x8000;
    	
    	
    	Bitmap cachedBitmap = this.common.cache.getBitmap(255, cache_idx);
    	Bitmap b = new Bitmap(cx, cy, this.opt);
    	if (cachedBitmap != null && this.surface.backstore.getCurrent() == cachedBitmap.getBufferedImage())
    		this.surface.backstore.setCurrent(b);
    	
    	this.common.cache.putBitmap(255, cache_idx, b, 0);
    }
    
	private void processFrameMarker(RdpPacket_Localised data) throws RdesktopException {
		int action;

		action = data.getLittleEndian32();

		switch (action) {
			case TS_FRAME_START:
				this.frameMarkerIsSet = true;
				break;
			case TS_FRAME_END:
				if (! this.frameMarkerIsSet) {
					// Should ignore this message because the frame marker is not set
				}

				this.frameMarkerIsSet = false;
				this.surface.repaint_order();

				break;
			default:
				logger.error("[processFrameMarker] Unknown action: "+action);
				return;
		}
	}
    
    private void processAlternateOrders(RdpPacket_Localised data, int order_flags)
            throws OrderException, RdesktopException {
    	
    	if ((order_flags & 0x2) == 0) {   // Check the class order
    		logger.error("Parsing failed "+order_flags);
    		throw new OrderException("Order parsing failed!");
    	}
    	order_flags >>= 2;
    	switch (order_flags)
    	{
    		case RDP_ORDER_TS_ALTSEC_CREATE_OFFSCR_BITMAP:
    			processCreateOffscrBitmap(data);
    			break;
    		case RDP_ORDER_TS_ALTSEC_SWITCH_SURFACE:
    			processSwitchSurface(data);
    			break;
		case RDP_ORDER_TS_ALTSEC_FRAME_MARKER:
			processFrameMarker(data);
			break;
    		default:
    			logger.debug("Unknow Alternate order "+order_flags);
    	}
    }
    
    /**
     * Handle secondary, or caching, orders
     * @param data Packet containing secondary order
     * @throws OrderException
     * @throws RdesktopException
     */
    private void processSecondaryOrders(RdpPacket_Localised data) throws RdesktopException {
        int length = 0;
        int type = 0;
        int flags = 0;
        int next_order = 0;

        length = data.getLittleEndian16();
        flags = data.getLittleEndian16();
        type = data.get8();

        next_order = data.getPosition() + length + 7;

        switch (type) {

        case RDP_ORDER_RAW_BMPCACHE:
            logger.debug("Raw BitmapCache Order");
            this.processRawBitmapCache(data);
            break;

        case RDP_ORDER_COLCACHE:
            logger.debug("Colorcache Order");
            this.processColorCache(data);
            break;

        case RDP_ORDER_BMPCACHE:
            logger.debug("Bitmapcache Order");
            this.processBitmapCache(data);
            break;

        case RDP_ORDER_FONTCACHE:
            logger.debug("Fontcache Order");
            this.processFontCache(data);
            break;

        case RDP_ORDER_RAW_BMPCACHE2:
            logger.debug("Raw Bitmapcache V2 Order");
            try {
                this.process_bmpcache2(data, flags, false);
            } catch (IOException e) {
                throw new RdesktopException(e.getMessage());
            } /* uncompressed */
            break;

        case RDP_ORDER_BMPCACHE2:
            logger.debug("Bitmapcache V2 Order");
            try {
                this.process_bmpcache2(data, flags, true);
            } catch (IOException e) {
                throw new RdesktopException(e.getMessage());
            } /* compressed */
            break;
        case RDP_ORDER_JPEGCACHE:
        	logger.debug("Jpegcache Order");
        	
        	try {
        		this.process_jpegcache(data, flags, true);
        	} catch (IOException e) {
        		throw new RdesktopException(e.getMessage());
        	} /* compressed */
        	break;
            	

        default:
            logger.warn("Unimplemented 2ry Order type " + type);
        }

        data.setPosition(next_order);
    }

    /**
     * Process a raw bitmap and store it in the bitmap cache
     * @param data Packet containing raw bitmap data
     * @throws RdesktopException
     */
    private void processRawBitmapCache(RdpPacket_Localised data)
            throws RdesktopException {
        int cache_id = data.get8();
        data.incrementPosition(1); // pad
        int width = data.get8();
        int height = data.get8();
        int bpp = data.get8();
        int Bpp = (bpp + 7) / 8;
        int bufsize = data.getLittleEndian16();
        int cache_idx = data.getLittleEndian16();
        int pdata = data.getPosition();
        data.incrementPosition(bufsize);

        byte[] inverted = new byte[width * height * Bpp];
        int pinverted = (height - 1) * (width * Bpp);
        for (int y = 0; y < height; y++) {
            data.copyToByteArray(inverted, pinverted, pdata, width * Bpp);
            // data.copyToByteArray(inverted, (height - y - 1) * (width * Bpp),
            // y * (width * Bpp), width*Bpp);
            pinverted -= width * Bpp;
            pdata += width * Bpp;
        }

        this.common.cache.putBitmap(cache_id, cache_idx, new Bitmap(new Bitmap(this.opt).convertImage(
                inverted, Bpp), width, height, 0, 0, this.opt), 0);
    }

    /**
     * Process and store details of a colour cache
     * @param data Packet containing cache information
     * @throws RdesktopException
     */
    private void processColorCache(RdpPacket_Localised data)
            throws RdesktopException {
        byte[] palette = null;

        byte[] red = null;
        byte[] green = null;
        byte[] blue = null;
        int j = 0;

        int cache_id = data.get8();
        int n_colors = data.getLittleEndian16(); // Number of Colors in
                                                    // Palette

        palette = new byte[n_colors * 4];
        red = new byte[n_colors];
        green = new byte[n_colors];
        blue = new byte[n_colors];
        data.copyToByteArray(palette, 0, data.getPosition(), palette.length);
        data.incrementPosition(palette.length);
        for (int i = 0; i < n_colors; i++) {
            blue[i] = palette[j];
            green[i] = palette[j + 1];
            red[i] = palette[j + 2];
            // palette[j+3] is pad
            j += 4;
        }
        IndexColorModel cm = new IndexColorModel(8, n_colors, red, green, blue);
        this.common.cache.put_colourmap(cache_id, cm);
        // surface.registerPalette(cm);
    }

    /**
     * Process a compressed bitmap and store in the bitmap cache
     * @param data Packet containing compressed bitmap
     * @throws RdesktopException
     */
    private void processBitmapCache(RdpPacket_Localised data)
            throws RdesktopException {
        @SuppressWarnings("unused")
		int bufsize, pad2, row_size, final_size, size;
        bufsize = pad2 = row_size = final_size = size = 0;

        int cache_id = data.get8();
        @SuppressWarnings("unused")
		int pad1 = data.get8(); // pad
        int width = data.get8();
        int height = data.get8();
        int bpp = data.get8();
        int Bpp = (bpp + 7) / 8;
        bufsize = data.getLittleEndian16(); // bufsize
        int cache_idx = data.getLittleEndian16();
        Bitmap bmp = new Bitmap(this.opt);

        /*
         * data.incrementPosition(2); // pad int size =
         * data.getLittleEndian16(); data.incrementPosition(4); // row_size,
         * final_size
         */

        if (this.opt.use_rdp5) {

            /* Begin compressedBitmapData */
            pad2 = data.getLittleEndian16(); // in_uint16_le(s, pad2); /* pad
                                                // */
            size = data.getLittleEndian16(); // in_uint16_le(s, size);
            row_size = data.getLittleEndian16(); // in_uint16_le(s,
                                                    // row_size);
            final_size = data.getLittleEndian16(); // in_uint16_le(s,
                                                    // final_size);

        } else {
            data.incrementPosition(2); // pad
            size = data.getLittleEndian16();
            row_size = data.getLittleEndian16(); // in_uint16_le(s,
                                                    // row_size);
            final_size = data.getLittleEndian16(); // in_uint16_le(s,
                                                    // final_size);
            // this is what's in rdesktop, but doesn't seem to work
            // size = bufsize;
        }

        // logger.info("BMPCACHE(cx=" + width + ",cy=" + height + ",id=" +
        // cache_id + ",idx=" + cache_idx + ",bpp=" + bpp + ",size=" + size +
        // ",pad1=" + pad1 + ",bufsize=" + bufsize + ",pad2=" + pad2 + ",rs=" +
        // row_size + ",fs=" + final_size + ")");

        if (Bpp == 1) {
            byte[] pixel = Bitmap.decompress(width, height, size, data, Bpp);
            if (pixel != null)
                this.common.cache.putBitmap(cache_id, cache_idx, new Bitmap(bmp
                        .convertImage(pixel, Bpp), width, height, 0, 0, this.opt), 0);
            else
                logger.warn("Failed to decompress bitmap");
        } else {
        	int[] pixel;
        	if (Bitmap.jniAvailable()) {
        		try {
        			byte[] compressed_pixel = new byte[size];
        			data.copyToByteArray(compressed_pixel, 0, data.getPosition(), size);
        			data.incrementPosition(size);
        			pixel = bmp.nRLEDecompress(width, height, size, compressed_pixel, Bpp);
        		} catch (UnsatisfiedLinkError e) {
        			logger.error("Unable to use jni method to decompress image");
        			pixel = bmp.decompressInt(width, height, size, data, Bpp);
        		}
        	} else {
        		pixel = bmp.decompressInt(width, height, size, data, Bpp);
        	}
        	if (pixel != null)
        		this.common.cache.putBitmap(cache_id, cache_idx, new Bitmap(pixel, width,
        			height, 0, 0, this.opt), 0);
        	else
        		logger.warn("Failed to decompress bitmap");
        }
    }

    /**
     * Process a jpeg cache order, storing a bitmap in the main cache, and
     * the persistant cache if so required
@@ -486,6 +520,87 @@
     * @throws RdesktopException
     * @throws IOException
     */
    private void process_jpegcache(RdpPacket_Localised data, int flags, boolean compressed) throws RdesktopException, IOException {
        Bitmap bitmap;
        int cache_id, cache_idx_low, width, height, Bpp;
        int cache_idx, bufsize;
        byte[] bitmap_id;

        bitmap_id = new byte[8]; /* prevent compiler warning */
        cache_id = flags & ID_MASK;
        Bpp = ((flags & MODE_MASK) >> MODE_SHIFT) - 2;
        if ((flags & PERSIST) != 0) {
            data.copyToByteArray(bitmap_id, 0, data.getPosition(), 8);
            data.incrementPosition(8);
    
        }

        if ((flags & SQUARE) != 0) {
            width = data.get8(); // in_uint8(s, width);
            height = width;
        } else {
            width = data.get8(); // in_uint8(s, width);
            height = data.get8(); // in_uint8(s, height);
        }

        bufsize = data.getBigEndian16(); // in_uint16_be(s, bufsize);
        bufsize &= BUFSIZE_MASK;

        cache_idx = data.get8(); // in_uint8(s, cache_idx);

        if ((cache_idx & LONG_FORMAT) != 0) {
            cache_idx_low = data.get8(); // in_uint8(s, cache_idx_low);
            cache_idx = ((cache_idx ^ LONG_FORMAT) << 8) + cache_idx_low;
        }
        
        logger.debug("JPEGCACHE(compr=" + compressed + ",flags=" + flags
                + ",cx=" + width + ",cy=" + height + ",id=" + cache_id
                + ",idx=" + cache_idx + ",Bpp=" + Bpp + ",bs=" + bufsize + ")");
        
        int[] bmpdataInt = null;
        byte[] compressed_pixel = new byte[bufsize];
        data.copyToByteArray(compressed_pixel, 0, data.getPosition(), bufsize);
        data.incrementPosition(bufsize);
        if (Bitmap.jniAvailable()) {
          bmpdataInt = Bitmap.nJpegDecompress(compressed_pixel, bufsize, width, height);
        } else {
          bmpdataInt = new int[width * height];
          ByteArrayInputStream bais = new ByteArrayInputStream(compressed_pixel);
          BufferedImage bi = ImageIO.read(bais);
          bmpdataInt = bi.getRGB(0, 0, width, height, null, 0, width);
          bais.close();
          bais = null;
        }
        if (bmpdataInt != null) {
          bitmap = new Bitmap(bmpdataInt, width, height, 0, 0, this.opt);
          this.common.cache.putBitmap(cache_id, cache_idx, bitmap, 0);
        }
    }

    /* Process a bitmap cache v2 order */
    
    /**
     * Process a bitmap cache v2 order, storing a bitmap in the main cache, and
     * the persistant cache if so required
     * @param data Packet containing order and bitmap data
     * @param flags Set of flags defining mode of order
     * @param compressed True if bitmap data is compressed
     * @throws RdesktopException
     * @throws IOException
     */
    private void process_bmpcache2(RdpPacket_Localised data, int flags,
            boolean compressed) throws RdesktopException, IOException {
	    
        Bitmap bitmap;
        Bitmap bmp = new Bitmap(this.opt);
        int y;
        int cache_id, cache_idx_low, width, height, Bpp;
        int cache_idx, bufsize;
        byte[] bmpdata, bitmap_id;

        bitmap_id = new byte[8]; /* prevent compiler warning */
        cache_id = flags & ID_MASK;
        Bpp = ((flags & MODE_MASK) >> MODE_SHIFT) - 2;
        if ((flags & PERSIST) != 0) {
            data.copyToByteArray(bitmap_id, 0, data.getPosition(), 8);
	    data.incrementPosition(8);
        }

        if ((flags & SQUARE) != 0) {
            width = data.get8(); // in_uint8(s, width);
            height = width;
        } else {
            width = data.get8(); // in_uint8(s, width);
            height = data.get8(); // in_uint8(s, height);
        }

        bufsize = data.getBigEndian16(); // in_uint16_be(s, bufsize);
        bufsize &= BUFSIZE_MASK;
        cache_idx = data.get8(); // in_uint8(s, cache_idx);

        if ((cache_idx & LONG_FORMAT) != 0) {
            cache_idx_low = data.get8(); // in_uint8(s, cache_idx_low);
            cache_idx = ((cache_idx ^ LONG_FORMAT) << 8) + cache_idx_low;
        }

        // in_uint8p(s, data, bufsize);

        logger.debug("BMPCACHE2(compr=" + compressed + ",flags=" + flags
                + ",cx=" + width + ",cy=" + height + ",id=" + cache_id
                + ",idx=" + cache_idx + ",Bpp=" + Bpp + ",bs=" + bufsize + ")");

        bmpdata = new byte[width * height * Bpp];
        int[] bmpdataInt = new int[width * height];

        if (compressed) {
            if (Bpp == 1) {
		    bmpdata = Bitmap.decompress(width, height, bufsize, data, Bpp);
		    bmpdataInt = bmp.convertImage(bmpdata, Bpp);
	    }
            else {
            	if (Bitmap.jniAvailable()) {
            		try {
            			byte[] compressed_pixel = new byte[bufsize];
            			data.copyToByteArray(compressed_pixel, 0, data.getPosition(), bufsize);
            			data.incrementPosition(bufsize);
            			bmpdataInt = bmp.nRLEDecompress(width, height, bufsize, compressed_pixel, Bpp);
            		} catch (UnsatisfiedLinkError e) {
            			logger.error("Unable to use jni method to decompress image");
            			bmpdataInt = bmp.decompressInt(width, height, bufsize, data, Bpp);
            		}
            	} else {
            		bmpdataInt = bmp.decompressInt(width, height, bufsize, data, Bpp);
            	}
            	if (bmpdataInt != null)
            		this.common.cache.putBitmap(cache_id, cache_idx, new Bitmap(bmpdataInt, width,
            			height, 0, 0, this.opt), 0);
            	else
            		logger.warn("Failed to decompress bitmap");
		
		for (int i = 0; i < bmpdataInt.length; i ++) {
			if (Bpp == 1)
				bmpdata[i * Bpp] = (byte) (bmpdataInt[i] & 0x000000FF);
			else if (Bpp == 2) {
				int r, g, b;
				b = bmpdataInt[i] & 0x000000FF;
				g = (bmpdataInt[i] & 0x0000FF00) >> 8;
				r = (bmpdataInt[i] & 0x00FF0000) >> 16;

				int pixel = 0x00000000;
				if (this.opt.server_bpp == 15)
					pixel = ((r & 0xF8) << 7) | ((g & 0xF8) << 2) | ((b & 0xF8) >> 3);
				else // 16 bits
					pixel = ((r & 0xF8) << 8) | ((g & 0xF8) << 3) | ((b & 0xF8) >> 3);

				bmpdata[i * Bpp+1] = (byte) ((pixel & 0x0000FF00) >> 8);
				bmpdata[i * Bpp] = (byte) (pixel & 0x000000FF);
			}
			else if (Bpp == 3 || Bpp == 4) {
				bmpdata[i * Bpp] = (byte) (bmpdataInt[i] & 0x000000FF);
				bmpdata[i * Bpp + 1] = (byte) ((bmpdataInt[i] & 0x0000FF00) >> 8);
				bmpdata[i * Bpp + 2] = (byte) ((bmpdataInt[i] & 0x00FF0000) >> 16);
			}
		}
	    }
		
            if (bmpdataInt == null) {
                logger.debug("Failed to decompress bitmap data");
                // xfree(bmpdata);
                return;
            }
            bitmap = new Bitmap(bmpdataInt, width, height, 0, 0, this.opt);
        } else {
		int pos_data = data.getPosition();
		int offset;
		for (y = 0; y < height; y++) {
			offset = pos_data + ((height - y - 1) * (width * Bpp));

			data.copyToByteArray(
				bmpdata,		// dest
				y * (width * Bpp),	// dest offset
				offset,			// mem offset
				width * Bpp);		// size
		}

		bitmap = new Bitmap(bmp.convertImage(bmpdata, Bpp), width, height, 0, 0, this.opt);
        }

        // bitmap = ui_create_bitmap(width, height, bmpdata);

    	this.common.cache.putBitmap(cache_id, cache_idx, bitmap, 0);
        // cache_put_bitmap(cache_id, cache_idx, bitmap, 0);
        if ((flags & PERSIST) != 0 && this.opt.persistent_bitmap_caching)
            this.common.persistent_cache.pstcache_put_bitmap(cache_id, cache_idx, bitmap_id,
                    width, height, width * height * Bpp, bmpdata);

        // xfree(bmpdata);
    }

    /**
     * Process a font caching order, and store font in the cache
     * @param data Packet containing font cache order, with data for a series of glyphs representing a font
     * @throws RdesktopException
     */
    private void processFontCache(RdpPacket_Localised data)
            throws RdesktopException {
        Glyph glyph = null;

        int font = 0, nglyphs = 0;
        int character = 0, offset = 0, baseline = 0, width = 0, height = 0;
        int datasize = 0;
        byte[] fontdata = null;

        font = data.get8();
        nglyphs = data.get8();

        for (int i = 0; i < nglyphs; i++) {
            character = data.getLittleEndian16();
            offset = data.getLittleEndian16();
            baseline = data.getLittleEndian16();
            width = data.getLittleEndian16();
            height = data.getLittleEndian16();
            datasize = (height * ((width + 7) / 8) + 3) & ~3;
            fontdata = new byte[datasize];

            data.copyToByteArray(fontdata, 0, data.getPosition(), datasize);
            data.incrementPosition(datasize);
            glyph = new Glyph(font, character, offset, baseline, width, height,
                    fontdata);
            this.common.cache.putFont(glyph);
        }
    }

    /**
     * Process a dest blit order, and perform blit on drawing surface
     * @param data Packet containing description of the order
     * @param destblt DestBltOrder object in which to store the blit description
     * @param present Flags defining the information available in the packet
     * @param delta True if the coordinates of the blit destination are described as relative to the source
     */
    private void processDestBlt(RdpPacket_Localised data, DestBltOrder destblt,
            int present, boolean delta) {
        if ((present & 0x01) != 0)
            destblt.setX(setCoordinate(data, destblt.getX(), delta));
        if ((present & 0x02) != 0)
            destblt.setY(setCoordinate(data, destblt.getY(), delta));
        if ((present & 0x04) != 0)
            destblt.setCX(setCoordinate(data, destblt.getCX(), delta));
        if ((present & 0x08) != 0)
            destblt.setCY(setCoordinate(data, destblt.getCY(), delta));
        if ((present & 0x10) != 0)
            destblt.setOpcode(ROP2_S(data.get8()));
        // if(logger.isInfoEnabled())
        // logger.info("opcode="+destblt.getOpcode());
        surface.drawDestBltOrder(destblt);
    }

    /**
     * Parse data defining a brush and store brush information
     * @param data Packet containing brush data
     * @param brush Brush object in which to store the brush description
     * @param present Flags defining the information available within the packet
     */
    private void parseBrush(RdpPacket_Localised data, Brush brush, int present) {
        if ((present & 0x01) != 0)
            brush.setXOrigin(data.get8());
        if ((present & 0x02) != 0)
            brush.setXOrigin(data.get8());
        if ((present & 0x04) != 0)
            brush.setStyle(data.get8());
        byte[] pat = brush.getPattern();
        if ((present & 0x08) != 0)
            pat[0] = (byte) data.get8();
        if ((present & 0x10) != 0)
            for (int i = 1; i < 8; i++)
                pat[i] = (byte) data.get8();
        brush.setPattern(pat);
    }

    /**
     * Parse data describing a pattern blit, and perform blit on drawing surface
     * @param data Packet containing blit data
     * @param patblt PatBltOrder object in which to store the blit description
     * @param present Flags defining the information available within the packet
     * @param delta True if the coordinates of the blit destination are described as relative to the source
     */
    private void processPatBlt(RdpPacket_Localised data, PatBltOrder patblt,
            int present, boolean delta) {
        if ((present & 0x01) != 0)
            patblt.setX(setCoordinate(data, patblt.getX(), delta));
        if ((present & 0x02) != 0)
            patblt.setY(setCoordinate(data, patblt.getY(), delta));
        if ((present & 0x04) != 0)
            patblt.setCX(setCoordinate(data, patblt.getCX(), delta));
        if ((present & 0x08) != 0)
            patblt.setCY(setCoordinate(data, patblt.getCY(), delta));
        if ((present & 0x10) != 0)
            patblt.setOpcode(ROP2_P(data.get8()));
        if ((present & 0x20) != 0)
            patblt.setBackgroundColor(setColor(data));
        if ((present & 0x40) != 0)
            patblt.setForegroundColor(setColor(data));
        parseBrush(data, patblt.getBrush(), present >> 7);
        // if(logger.isInfoEnabled()) logger.info("opcode="+patblt.getOpcode());
        surface.drawPatBltOrder(patblt);
    }

    /**
     * Parse data describing a screen blit, and perform blit on drawing surface
     * @param data Packet containing blit data
     * @param screenblt ScreenBltOrder object in which to store blit description
     * @param present Flags defining the information available within the packet
     * @param delta True if the coordinates of the blit destination are described as relative to the source
     */
    private void processScreenBlt(RdpPacket_Localised data,
            ScreenBltOrder screenblt, int present, boolean delta) {
        if ((present & 0x01) != 0)
            screenblt.setX(setCoordinate(data, screenblt.getX(), delta));
        if ((present & 0x02) != 0)
            screenblt.setY(setCoordinate(data, screenblt.getY(), delta));
        if ((present & 0x04) != 0)
            screenblt.setCX(setCoordinate(data, screenblt.getCX(), delta));
        if ((present & 0x08) != 0)
            screenblt.setCY(setCoordinate(data, screenblt.getCY(), delta));
        if ((present & 0x10) != 0)
            screenblt.setOpcode(ROP2_S(data.get8()));
        if ((present & 0x20) != 0)
            screenblt.setSrcX(setCoordinate(data, screenblt.getSrcX(), delta));
        if ((present & 0x40) != 0)
            screenblt.setSrcY(setCoordinate(data, screenblt.getSrcY(), delta));
        // if(logger.isInfoEnabled())
        // logger.info("opcode="+screenblt.getOpcode());
        surface.drawScreenBltOrder(screenblt);
    }

    /**
     * Parse data describing a line order, and draw line on drawing surface
     * @param data Packet containing line order data
     * @param line LineOrder object describing the line drawing operation
     * @param present Flags defining the information available within the packet
     * @param delta True if the coordinates of the end of the line are defined as relative to the start
     */
    private void processLine(RdpPacket_Localised data, LineOrder line,
            int present, boolean delta) {
        if ((present & 0x01) != 0)
            line.setMixmode(data.getLittleEndian16());
        if ((present & 0x02) != 0)
            line.setStartX(setCoordinate(data, line.getStartX(), delta));
        if ((present & 0x04) != 0)
            line.setStartY(setCoordinate(data, line.getStartY(), delta));
        if ((present & 0x08) != 0)
            line.setEndX(setCoordinate(data, line.getEndX(), delta));
        if ((present & 0x10) != 0)
            line.setEndY(setCoordinate(data, line.getEndY(), delta));
        if ((present & 0x20) != 0)
            line.setBackgroundColor(setColor(data));
        if ((present & 0x40) != 0)
            line.setOpcode(data.get8());

        parsePen(data, line.getPen(), present >> 7);

        // if(logger.isInfoEnabled()) logger.info("Line from
        // ("+line.getStartX()+","+line.getStartY()+") to
        // ("+line.getEndX()+","+line.getEndY()+")");

        if (line.getOpcode() < 0x01 || line.getOpcode() > 0x10) {
            logger.warn("bad ROP2 0x" + line.getOpcode());
            return;
        }

        // now draw the line
        surface.drawLineOrder(line);
    }

    /**
     * Parse data describing a rectangle order, and draw the rectangle to the drawing surface
     * @param data Packet containing rectangle order
     * @param rect RectangleOrder object in which to store order description
     * @param present Flags defining information available in packet
     * @param delta True if the rectangle is described as (x,y,width,height), as opposed to (x1,y1,x2,y2)
     */
    private void processRectangle(RdpPacket_Localised data,
            RectangleOrder rect, int present, boolean delta) {
        if ((present & 0x01) != 0)
            rect.setX(setCoordinate(data, rect.getX(), delta));
        if ((present & 0x02) != 0)
            rect.setY(setCoordinate(data, rect.getY(), delta));
        if ((present & 0x04) != 0)
            rect.setCX(setCoordinate(data, rect.getCX(), delta));
        if ((present & 0x08) != 0)
            rect.setCY(setCoordinate(data, rect.getCY(), delta));
        if ((present & 0x10) != 0)
            this.rect_colour = (this.rect_colour & 0xffffff00) | data.get8(); // rect.setColor(setColor(data));
        if ((present & 0x20) != 0)
            this.rect_colour = (this.rect_colour & 0xffff00ff)
                    | (data.get8() << 8); // rect.setColor(setColor(data));
        if ((present & 0x40) != 0)
            this.rect_colour = (this.rect_colour & 0xff00ffff)
                    | (data.get8() << 16);

        rect.setColor(this.rect_colour);
        surface.drawRectangleOrder(rect);
    }

    /**
     * Parse data describing a desktop save order, either saving the desktop to cache, or drawing a section to screen
     * @param data Packet containing desktop save order
     * @param desksave DeskSaveOrder object in which to store order description
     * @param present Flags defining information available within the packet
     * @param delta True if destination coordinates are described as relative to the source
     * @throws RdesktopException
     */
    private void processDeskSave(RdpPacket_Localised data,
            DeskSaveOrder desksave, int present, boolean delta)
            throws RdesktopException {
        int width = 0, height = 0;

        if ((present & 0x01) != 0) {
            desksave.setOffset(data.getLittleEndian32());
        }

        if ((present & 0x02) != 0) {
            desksave.setLeft(setCoordinate(data, desksave.getLeft(), delta));
        }

        if ((present & 0x04) != 0) {
            desksave.setTop(setCoordinate(data, desksave.getTop(), delta));
        }

        if ((present & 0x08) != 0) {
            desksave.setRight(setCoordinate(data, desksave.getRight(), delta));
        }

        if ((present & 0x10) != 0) {
            desksave
                    .setBottom(setCoordinate(data, desksave.getBottom(), delta));
        }

        if ((present & 0x20) != 0) {
            desksave.setAction(data.get8());
        }

        width = desksave.getRight() - desksave.getLeft() + 1;
        height = desksave.getBottom() - desksave.getTop() + 1;

        if (desksave.getAction() == 0) {
            int[] pixel = surface.getImage(desksave.getLeft(), desksave
                    .getTop(), width, height);
            this.common.cache.putDesktop((int) desksave.getOffset(), width, height, pixel);
        } else {
            int[] pixel = this.common.cache.getDesktopInt((int) desksave.getOffset(),
                    width, height);
            surface.putImage(desksave.getLeft(), desksave.getTop(), width,
                    height, pixel);
        }
    }

    /**
     * Process data describing a memory blit, and perform blit on drawing surface
     * @param data Packet containing mem blit order
     * @param memblt MemBltOrder object in which to store description of blit
     * @param present Flags defining information available in packet
     * @param delta True if destination coordinates are described as relative to the source
     */
    private void processMemBlt(RdpPacket_Localised data, MemBltOrder memblt,
            int present, boolean delta) {
        if ((present & 0x01) != 0) {
            memblt.setCacheID(data.get8());
            memblt.setColorTable(data.get8());
        }
        if ((present & 0x02) != 0)
            memblt.setX(setCoordinate(data, memblt.getX(), delta));
        if ((present & 0x04) != 0)
            memblt.setY(setCoordinate(data, memblt.getY(), delta));
        if ((present & 0x08) != 0)
            memblt.setCX(setCoordinate(data, memblt.getCX(), delta));
        if ((present & 0x10) != 0)
            memblt.setCY(setCoordinate(data, memblt.getCY(), delta));
        if ((present & 0x20) != 0)
            memblt.setOpcode(ROP2_S(data.get8()));
        if ((present & 0x40) != 0)
            memblt.setSrcX(setCoordinate(data, memblt.getSrcX(), delta));
        if ((present & 0x80) != 0)
            memblt.setSrcY(setCoordinate(data, memblt.getSrcY(), delta));
        if ((present & 0x0100) != 0)
            memblt.setCacheIDX(data.getLittleEndian16());

        // if(logger.isInfoEnabled()) logger.info("Memblt
        // opcode="+memblt.getOpcode());
        surface.drawMemBltOrder(memblt);
    }

    /**
     * Parse data describing a tri blit order, and perform blit on drawing surface
     * @param data Packet containing tri blit order
     * @param triblt TriBltOrder object in which to store blit description 
     * @param present Flags defining information available in packet
     * @param delta True if destination coordinates are described as relative to the source
     */
    private void processTriBlt(RdpPacket_Localised data, TriBltOrder triblt,
            int present, boolean delta) {
        if ((present & 0x01) != 0) {
            triblt.setCacheID(data.get8());
            triblt.setColorTable(data.get8());
        }
        if ((present & 0x02) != 0)
            triblt.setX(setCoordinate(data, triblt.getX(), delta));
        if ((present & 0x04) != 0)
            triblt.setY(setCoordinate(data, triblt.getY(), delta));
        if ((present & 0x08) != 0)
            triblt.setCX(setCoordinate(data, triblt.getCX(), delta));
        if ((present & 0x10) != 0)
            triblt.setCY(setCoordinate(data, triblt.getCY(), delta));
        if ((present & 0x20) != 0)
            triblt.setOpcode(ROP2_S(data.get8()));
        if ((present & 0x40) != 0)
            triblt.setSrcX(setCoordinate(data, triblt.getSrcX(), delta));
        if ((present & 0x80) != 0)
            triblt.setSrcY(setCoordinate(data, triblt.getSrcY(), delta));
        if ((present & 0x0100) != 0)
            triblt.setBackgroundColor(setColor(data));
        if ((present & 0x0200) != 0)
            triblt.setForegroundColor(setColor(data));

        parseBrush(data, triblt.getBrush(), present >> 10);

        if ((present & 0x8000) != 0)
            triblt.setCacheIDX(data.getLittleEndian16());
        if ((present & 0x10000) != 0)
            triblt.setUnknown(data.getLittleEndian16());

        surface.drawTriBltOrder(triblt);
    }

    /**
     * Parse data describing a multi-line order, and draw to registered surface
     * @param data Packet containing polyline order
     * @param polyline PolyLineOrder object in which to store order description 
     * @param present Flags defining information available in packet
     * @param delta True if each set of coordinates is described relative to previous set
     */
    private void processPolyLine(RdpPacket_Localised data,
            PolyLineOrder polyline, int present, boolean delta) {
        if ((present & 0x01) != 0)
            polyline.setX(setCoordinate(data, polyline.getX(), delta));
        if ((present & 0x02) != 0)
            polyline.setY(setCoordinate(data, polyline.getY(), delta));
        if ((present & 0x04) != 0)
            polyline.setOpcode(data.get8());
        if ((present & 0x10) != 0)
            polyline.setForegroundColor(setColor(data));
        if ((present & 0x20) != 0)
            polyline.setLines(data.get8());
        if ((present & 0x40) != 0) {
            int datasize = data.get8();
            polyline.setDataSize(datasize);
            byte[] databytes = new byte[datasize];
            for (int i = 0; i < datasize; i++)
                databytes[i] = (byte) data.get8();
            polyline.setData(databytes);
        }
        // logger.info("polyline delta="+delta);
        // if(logger.isInfoEnabled()) logger.info("Line from
        // ("+line.getStartX()+","+line.getStartY()+") to
        // ("+line.getEndX()+","+line.getEndY()+")");
        // now draw the line
        surface.drawPolyLineOrder(polyline);
    }

    /**
     * Process a text2 order and output to drawing surface
     * @param data Packet containing text2 order
     * @param text2 Text2Order object in which to store order description
     * @param present Flags defining information available in packet
     * @param delta Unused
     * @throws RdesktopException
     */
    private void processText2(RdpPacket_Localised data, Text2Order text2,
            int present, boolean delta) throws RdesktopException {

        if ((present & 0x000001) != 0) {
            text2.setFont(data.get8());                     // cacheId (optional)
        }
        if ((present & 0x000002) != 0) {
            text2.setFlags(data.get8());                    // flAccel (optional)
        }

        if ((present & 0x000004) != 0) {
            text2.setOpcode(data.get8());                   // ulCharInc (optional)
        }

        if ((present & 0x000008) != 0) {
            text2.setMixmode(data.get8());                  // fOpRedundant (optional)
        }

        if ((present & 0x000010) != 0) {
            text2.setForegroundColor(setColor(data));       // BackColor (optional)
        }

        if ((present & 0x000020) != 0) {
            text2.setBackgroundColor(setColor(data));       // ForeColor (optional)
        }

        if ((present & 0x000040) != 0) {
            text2.setClipLeft(data.getLittleEndian16());    // BkLeft (optional)
        }

        if ((present & 0x000080) != 0) {
            text2.setClipTop(data.getLittleEndian16());     // BkTop (optional)
        }

        if ((present & 0x000100) != 0) {
            text2.setClipRight(data.getLittleEndian16());   // BkRight (optional)
        }

        if ((present & 0x000200) != 0) {
            text2.setClipBottom(data.getLittleEndian16());  // BkBottom (optional)
        }

        if ((present & 0x000400) != 0) {
            text2.setBoxLeft(data.getLittleEndian16());     // OpLeft (optional)
        }

        if ((present & 0x000800) != 0) {
            text2.setBoxTop(data.getLittleEndian16());      // OpTop (optional)
        }

        if ((present & 0x001000) != 0) {
            text2.setBoxRight(data.getLittleEndian16());    // OpRight (optional)
        }

        if ((present & 0x002000) != 0) {
            text2.setBoxBottom(data.getLittleEndian16());   // OpBottom (optional)
        }

        this.parseBrush(data, text2.getBrush(), present>>14);

        if ((present & 0x080000) != 0) {
            text2.setX(data.getLittleEndian16());           // X (optional)
        }

        if ((present & 0x100000) != 0) {
            text2.setY(data.getLittleEndian16());           // Y (optional)
        }

        if ((present & 0x200000) != 0) {
            text2.setLength(data.get8());                   // VariableBytes (variable)

            byte[] text = new byte[text2.getLength()];
            data.copyToByteArray(text, 0, data.getPosition(), text.length);
            data.incrementPosition(text.length);
            text2.setText(text);

            /*
             * if(logger.isInfoEnabled()) logger.info("X: " + text2.getX() + "
             * Y: " + text2.getY() + " Left Clip: " + text2.getClipLeft() + "
             * Top Clip: " + text2.getClipTop() + " Right Clip: " +
             * text2.getClipRight() + " Bottom Clip: " + text2.getClipBottom() + "
             * Left Box: " + text2.getBoxLeft() + " Top Box: " +
             * text2.getBoxTop() + " Right Box: " + text2.getBoxRight() + "
             * Bottom Box: " + text2.getBoxBottom() + " Foreground Color: " +
             * text2.getForegroundColor() + " Background Color: " +
             * text2.getBackgroundColor() + " Font: " + text2.getFont() + "
             * Flags: " + text2.getFlags() + " Mixmode: " + text2.getMixmode() + "
             * Unknown: " + text2.getUnknown() + " Length: " +
             * text2.getLength());
             */
        }

        this.drawText(text2, text2.getClipRight() - text2.getClipLeft(), text2
                .getClipBottom()
                - text2.getClipTop(), text2.getBoxRight() - text2.getBoxLeft(),
                text2.getBoxBottom() - text2.getBoxTop());

    }

    /**
     * Parse a description for a bounding box
     * @param data Packet containing order defining bounding box
     * @param bounds BoundsOrder object in which to store description of bounds
     * @throws OrderException
     */
    private void parseBounds(RdpPacket_Localised data, BoundsOrder bounds)
            throws OrderException {
        int present = 0;

        present = data.get8();

        if ((present & 1) != 0) {
            bounds.setLeft(setCoordinate(data, bounds.getLeft(), false));
        } else if ((present & 16) != 0) {
            bounds.setLeft(setCoordinate(data, bounds.getLeft(), true));
        }

        if ((present & 2) != 0) {
            bounds.setTop(setCoordinate(data, bounds.getTop(), false));
        } else if ((present & 32) != 0) {
            bounds.setTop(setCoordinate(data, bounds.getTop(), true));
        }

        if ((present & 4) != 0) {
            bounds.setRight(setCoordinate(data, bounds.getRight(), false));
        } else if ((present & 64) != 0) {
            bounds.setRight(setCoordinate(data, bounds.getRight(), true));
        }

        if ((present & 8) != 0) {
            bounds.setBottom(setCoordinate(data, bounds.getBottom(), false));
        } else if ((present & 128) != 0) {
            bounds.setBottom(setCoordinate(data, bounds.getBottom(), true));
        }

        if (data.getPosition() > data.getEnd()) {
            throw new OrderException("Too far!");
        }
    }

    /**
     * Retrieve a coordinate from a packet and return as an absolute integer coordinate
     * @param data Packet containing coordinate at current read position
     * @param coordinate Offset coordinate
     * @param delta True if coordinate being read should be taken as relative to offset coordinate, false if absolute
     * @return Integer value of coordinate stored in packet, in absolute form
     */
    private static int setCoordinate(RdpPacket_Localised data, int coordinate,
            boolean delta) {
        byte change = 0;

        if (delta) {
            change = (byte) data.get8();
            coordinate += (int) change;
            return coordinate;
        } else {
            coordinate = data.getLittleEndian16();
            return coordinate;
        }
    }

    /**
     * Read a colour value from a packet
     * @param data Packet containing colour value at current read position
     * @return Integer colour value read from packet
     */
    private static int setColor(RdpPacket_Localised data) {
        int color = 0;
        int i = 0;

        i = data.get8(); // in_uint8(s, i);
        color = i; // *colour = i;
        i = data.get8(); // in_uint8(s, i);
        color |= i << 8; // *colour |= i << 8;
        i = data.get8(); // in_uint8(s, i);
        color |= i << 16; // *colour |= i << 16;

        // color = data.get8();
        // data.incrementPosition(2);
        return color;
    }

    /**
     * Set current cache
     * @param cache Cache object to set as current global cache
     */
    public void registerCache(Cache cache) {
        this.common.cache = cache;
    }

    /**
     * Parse a pen definition
     * @param data Packet containing pen description at current read position
     * @param pen Pen object in which to store pen description
     * @param present Flags defining information available within packet
     * @return True if successful
     */
    private static boolean parsePen(RdpPacket_Localised data, Pen pen,
            int present) {
        if ((present & 0x01) != 0)
            pen.setStyle(data.get8());
        if ((present & 0x02) != 0)
            pen.setWidth(data.get8());
        if ((present & 0x04) != 0)
            pen.setColor(setColor(data));

        return true; // return s_check(s);
    }

    /**
     * Interpret an integer as a 16-bit two's complement number, based on its binary representation
     * @param val Integer interpretation of binary number
     * @return 16-bit two's complement value of input
     */
    private int twosComplement16(int val) {
        return ((val & 0x8000) != 0) ? -((~val & 0xFFFF)+1) : val;
    }

    /**
     * Draw a text2 order to the drawing surface
     * @param text2 Text2Order describing text to be drawn
     * @param clipcx Width of clipping area
     * @param clipcy Height of clipping area
     * @param boxcx Width of bounding box (to draw if > 1)
     * @param boxcy Height of bounding box (to draw if boxcx > 1)
     * @throws RdesktopException
     */
private void drawText(Text2Order text2, int clipcx, int clipcy, int boxcx, int boxcy) throws RdesktopException {
	byte[] text = text2.getText();
	DataBlob entry = null;
	Glyph glyph = null;
	int offset = 0;
	int ptext = 0;
	int length = text2.getLength();
	int x = text2.getX();
	int y = text2.getY();

	if(boxcx > 1) {
	    surface.fillRectangle(text2.getBoxLeft(), text2.getBoxTop(), boxcx, boxcy, text2.getBackgroundColor());
	} else if (text2.getMixmode() == MIX_OPAQUE) {
	    surface.fillRectangle(text2.getClipLeft(), text2.getClipTop(), clipcx, clipcy, text2.getBackgroundColor());
	}
	
	/*
     * logger.debug("X: " + text2.getX() + " Y: " + text2.getY() + " Left Clip: " +
     * text2.getClipLeft() + " Top Clip: " + text2.getClipTop() + " Right Clip: " +
     * text2.getClipRight() + " Bottom Clip: " + text2.getClipBottom() + " Left
     * Box: " + text2.getBoxLeft() + " Top Box: " + text2.getBoxTop() + " Right
     * Box: " + text2.getBoxRight() + " Bottom Box: " + text2.getBoxBottom() + "
     * Foreground Color: " + text2.getForegroundColor() + " Background Color: " +
     * text2.getBackgroundColor() + " Font: " + text2.getFont() + " Flags: " +
     * text2.getFlags() + " Mixmode: " + text2.getMixmode() + " Unknown: " +
     * text2.getUnknown() + " Length: " + text2.getLength());
     */
	for(int i = 0; i < length;) {
	    switch(text[ptext + i]&0x000000ff) {
	    case (0xff):
			if(i + 2 < length) {
			    byte[] data = new byte[text[ptext + i + 2]&0x000000ff];
			    System.arraycopy(text ,ptext , data, 0, text[ptext + i + 2]&0x000000ff);
			    DataBlob db = new DataBlob(text[ptext + i + 2]&0x000000ff, data);
			    this.common.cache.putText(text[ptext + i + 1]&0x000000ff, db);
			} else {
			    throw new RdesktopException();
			}
			length -= i + 3;
			ptext = i + 3;
			i = 0;
			break;
		
	    case (0xfe):
			entry = this.common.cache.getText(text[ptext + i + 1]&0x000000ff);
			if(entry != null){
			    if((entry.getData()[1] == 0) && ((text2.getFlags() & TEXT2_IMPLICIT_X) == 0)) {
			        if((text2.getFlags() & 0x04) != 0) {
			            y += text[ptext + i + 2]&0x000000ff;
			        } else {
			            x += text[ptext + i + 2]&0x000000ff;
			        }
			    }
            }
			if(i + 2 < length) {
			    i += 3;
			} else {
			    i += 2;
			}
			length -= i;
			ptext = i;
			i = 0;
			if (entry == null)
				break;
            
			byte[] data = entry.getData();
			for(int j = 0; j < entry.getSize(); j++) {
			    glyph = this.common.cache.getFont(text2.getFont(), data[j]&0x000000ff);
			    if((text2.getFlags() & TEXT2_IMPLICIT_X) == 0) {
				offset = data[++j]&0x000000ff;
				if((offset & 0x80) !=0) {
                    if((text2.getFlags() & TEXT2_VERTICAL) != 0) {
                        int var = this.twosComplement16((data[j+1]&0xff) | ((data[j+2]&0xff) << 8));
                        y += var;
                        j += 2;
				    } else {
                        int var = this.twosComplement16((data[j+1]&0xff) | ((data[j+2]&0xff) << 8));
                        x += var;
                        j += 2;
				    }
				} else {
				    if((text2.getFlags() & TEXT2_VERTICAL) != 0) {
					y += offset;
				    } else {
					x += offset;
				    }
				}
			    }
			    if(glyph != null) {
                    //if((text2.getFlags() & TEXT2_VERTICAL) != 0) logger.info("Drawing glyph: (" + (x + (short)glyph.getOffset()) + ", " + (y + (short)glyph.getBaseLine()) + ")"  );
                    surface.drawGlyph(text2.getMixmode(), x + (short)glyph.getOffset(),
                                  y + (short)glyph.getBaseLine(), glyph.getWidth(),
                                  glyph.getHeight(), glyph.getFontData(),
                                  text2.getBackgroundColor(), text2.getForegroundColor());   
                
				if((text2.getFlags() & TEXT2_IMPLICIT_X) != 0) {
                   x += glyph.getWidth();
				}
			    }    
			}
			break;

	    default:
			glyph = this.common.cache.getFont(text2.getFont(), text[ptext + i]&0x000000ff);
			if((text2.getFlags() & TEXT2_IMPLICIT_X) == 0) {
			    offset = text[ptext + (++i)]&0x000000ff;
			    if((offset & 0x80) !=0) {
				if((text2.getFlags() & TEXT2_VERTICAL) != 0) {
				    //logger.info("y +=" + (text[ptext + (i+1)]&0x000000ff) + " | " + ((text[ptext + (i+2)]&0x000000ff) << 8));
                    int var = this.twosComplement16((text[ptext + i+1]&0x000000ff) | ((text[ptext + i+2]&0x000000ff) << 8));
                    y += var;
                    i += 2;
				} else {
                    int var = this.twosComplement16((text[ptext + i+1]&0x000000ff) | ((text[ptext + i+2]&0x000000ff) << 8));
                    x += var;
                    i += 2;
				}
			    } else {
				if((text2.getFlags() & TEXT2_VERTICAL) != 0) {
				    y += offset; 
				} else {
				    x += offset;
				}
			    }
			}
			if(glyph != null) {
			    surface.drawGlyph(text2.getMixmode(), x + (short)glyph.getOffset(),
					      y + (short)glyph.getBaseLine(), glyph.getWidth(),
					      glyph.getHeight(), glyph.getFontData(), 
					      text2.getBackgroundColor(), text2.getForegroundColor());
			    
			    if((text2.getFlags() & TEXT2_IMPLICIT_X) != 0) x += glyph.getWidth();
			}
			i++;
		break;
	    }
	}
    }}
