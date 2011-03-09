/**
  * Copyright (C)2011 by Richard Loos
  * All rights reserved.
  *
  * This file is part of the JarleVision client example program.
  *
  * JarleVision is free software: you can redistribute it and/or modify
  * it under the terms of the GNU General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.
  *
  * ParleVision is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU General Public License for more details.
  *
  * A copy of the GNU General Public License can be found in the root
  * of this software package directory in the file LICENSE.LGPL.
  * If not, see <http://www.gnu.org/licenses/>.
  */

import com.trolltech.qt.QNativePointer;
import com.trolltech.qt.core.QDataStream;
import com.trolltech.qt.gui.QImage;
import com.trolltech.qt.gui.QImage.Format;

/**
 * This class maps the plv::CvMatData wrapper around cv::Mat
 * to a QImage. It can load CvMatData from a QDataStream.
 * Because Qt is native code, the data lives in native memory space.
 * This class manages this memory and disposes of it at garbage collection.
 *
 * @author Richard Loos
 *
 */
public class CvMatData {
	QImage image;
	QNativePointer data;
	
	final static int CV_CN_MAX   = 64;
	final static int CV_CN_SHIFT = 3;
	
	final static int CV_8U 		 = 0;
	final static int CV_8S 		 = 1;
	final static int CV_16U 	 = 2;
	final static int CV_16S 	 = 3;
	final static int CV_32S 	 = 4;
	final static int CV_32F  	 = 5;
	final static int CV_64F  	 = 6;
	final static int CV_USRTYPE1 = 7;

	final static int CV_DEPTH_MAX 		= (1 << CV_CN_SHIFT);
	final static int CV_MAT_DEPTH_MASK 	= (CV_DEPTH_MAX - 1);
	final static int CV_MAT_CN_MASK   	= (CV_CN_MAX - 1) << CV_CN_SHIFT;
	final static int CV_MAT_TYPE_MASK 	= CV_DEPTH_MAX*CV_CN_MAX - 1;
	
	final static int CV_MAT_DEPTH(int flags) {
		return (flags) & CV_MAT_DEPTH_MASK; 
	}

	final static int CV_MAKE_TYPE(int depth, int cn) {
		return CV_MAT_DEPTH(depth) + ((cn -1) << CV_CN_SHIFT);
	}
	
	final static int CV_MAT_CN(int flags) {
		return (((flags) & CV_MAT_CN_MASK) >> CV_CN_SHIFT) + 1;
	}
			
	final static int CV_MAT_TYPE(int flags) {
		return (flags) & CV_MAT_TYPE_MASK;
	}
	
	public CvMatData()
	{
		image = null;
		data = null;
	}
	
	public void free()
	{
		assert( image != null );
		assert( data != null );
		
		// delete the image
		image.dispose();
		image = null;
		
		// this will delete the data
		data  = null;
	}
	
	public boolean isValid() {
		return image != null;
	}
	
	public QImage getImage() {
		return image;
	}
	
	@Override
	protected void finalize() throws Throwable {
		if( data != null )
		{
			System.err.println("Error: memory leak! Native pointer in CvMatData not freed explicitly!");
		}
		super.finalize();
	}
	
	final static String depthToString(int depth)
	{
		switch(depth)
		{
		case CV_8U:
			return new String("CV_8U");
		case CV_8S:
			return new String("CV_8S");
		case CV_16U:
			return new String("CV_16U");
		case CV_16S:
			return new String("CV_16S");
		case CV_32S:
			return new String("CV_32S");
		case CV_32F:
			return new String("CV_32F");
		case CV_64F:
			return new String("CV_64F");
		case CV_USRTYPE1:
			return new String("CV_USRTYPE1");
		default:
			return new String("Invalid");
		}
	}
	
	public boolean readFrom(QDataStream s) 
	{
		int type   = s.readInt();
	    int height = s.readInt();
	    int width  = s.readInt();
	    int length = s.readInt();
	    
	    // copy the input stream into a java buffer 
	    // in one go because this is faster
	    // than reading it byte for byte into
	    // a native buffer
	    byte[] buffer = new byte[length];
	    s.readBytes(buffer, length);
	    
	    int channels = CV_MAT_CN(type);
	    int depth    = CV_MAT_DEPTH(type);
	    
	    if( depth == CV_8U )
	    {
	    	if( channels == 1)
	    	{
	    		data = new QNativePointer(QNativePointer.Type.Byte, width*height);
	    		
	    		// copy the buffer back into the native buffer
	    		for( int i=0; i < width*height; ++i )
	    			data.setByteAt(i, buffer[i]);
	    		
	    	    image = new QImage(data, width, height, width, Format.Format_Indexed8);
	    	} 
	    	else if( channels == 3)
	    	{
	    		if(length == width * height * channels)
	    		{
	    			data = new QNativePointer(QNativePointer.Type.Int, width*height);
		    		for( int i=0; i < width*height; ++i)
		    		{
		    			int idx = i*3;
	    	    		
		    			// for some reason green and blue are reversed. We know OpenCV stores data
		    			// as BGR but this is RBG? 
		    			byte red   = buffer[idx];
		    			byte blue  = buffer[idx+1];
		    			byte green = buffer[idx+2];
	    	    		int argb = 0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff); 
	    	    		data.setIntAt(i, argb );
		    		}
		    	    image = new QImage(data, width, height, 
		    	    				   width * 4, // bytes per line: 1 int == 4 bytes
		    	    				   QImage.Format.Format_RGB32);
	    		}
	    	}
	    	else if( channels == 4)
	    	{
	    		if(length == width * height * channels)
	    		{
	    			data = new QNativePointer(QNativePointer.Type.Int, width*height);
		    		for( int i=0; i < width*height; ++i)
		    		{
		    			int idx = i*4;
	    	    		
		    			// big endian ARGB (so BGRA), 
		    			// we disregard alpha and convert to argb
		    			byte blue   = buffer[idx];
		    			byte green  = buffer[idx+1];
		    			byte red    = buffer[idx+2];
		    			int argb    = 0xff000000 | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff); 
	    	    		data.setIntAt(i, argb );
		    		}
		    	    image = new QImage(data, width, height, 
		    	    				   width * 4, // bytes per line: 1 int == 4 bytes
		    	    				   QImage.Format.Format_ARGB32);
	    		}
	    	}
	    }
	    
	    if( image == null )
	    {
	    	System.out.println("Failed to parse CvMatData with properties (" + depthToString(depth) + " " + width + "x" + height + "x" + channels 
		    		+ " " + length + " bytes )" );
	    	return false;
	    }
	    
	    //System.out.println("Parsed CvMatData (" + depthToString(depth) + " " + cols + "x" + rows + "x" + channels 
		//		+ " " + length + " bytes )" );
	    return true;
	}
}
