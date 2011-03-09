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
  
import com.trolltech.qt.QVariant;
import com.trolltech.qt.core.QBitArray;
import com.trolltech.qt.core.QByteArray;
import com.trolltech.qt.core.QDataStream;
import com.trolltech.qt.core.QIODevice;
import com.trolltech.qt.core.Qt;
import com.trolltech.qt.gui.*;
import com.trolltech.qt.network.*;

public class Client extends QWidget {

    private QLabel hostLabel;
    private QLabel portLabel;
    private QLabel statusLabel;
    private QLabel imageLabel;
    private QPushButton quitButton;
    private QPushButton connectButton;
    private QDialogButtonBox buttonBox;
    private QTcpSocket tcpSocket;
    private int frameSize;
    QLineEdit hostLineEdit;
    QLineEdit portLineEdit;
    String currentFortune;
    int serial;
    byte[] frameBuffer;
        
    final static int PROTO_FRAME = 0x000000;
    final static int PROTO_INIT  = 0x000001;
    final static int PROTO_ACK   = 0x000003;
        
    Client(QWidget parent) 
    {
    	hostLabel = new QLabel("Server address:");
        portLabel = new QLabel("Port:");
        
        hostLineEdit = new QLineEdit();
        portLineEdit = new QLineEdit();
        
        portLineEdit.setValidator(new QIntValidator(1, 65535, this));

        hostLabel.setBuddy(hostLineEdit);
        portLabel.setBuddy(portLineEdit);
        
        statusLabel = new QLabel(tr("This program requires that you run the Parlevision Server as well."));
        
        connectButton = new QPushButton(tr("Connect"));
        connectButton.setDefault(true);
        connectButton.setEnabled(false);

        quitButton = new QPushButton(tr("Quit"));

        buttonBox = new QDialogButtonBox();
        buttonBox.addButton( connectButton, QDialogButtonBox.ButtonRole.ActionRole);
        buttonBox.addButton( quitButton, QDialogButtonBox.ButtonRole.RejectRole);

        imageLabel = new QLabel();
        QImage img  = new QImage( 640, 480, QImage.Format.Format_ARGB32 );
        img.fill( new QColor( Qt.GlobalColor.black ).rgb() );
        imageLabel.setPixmap( QPixmap.fromImage( img ) );

        tcpSocket = new QTcpSocket(this);

        hostLineEdit.textChanged.connect( this, "enableConnectButton()" );
        portLineEdit.textChanged.connect( this, "enableConnectButton()" );
        
        connectButton.clicked.connect( this, "onConnectButtonPressed()" );
        quitButton.clicked.connect( this, "close()" );
        
        tcpSocket.readyRead.connect( this, "readFrame()" );
        tcpSocket.disconnected.connect( this, "onDisconnect()" );
        tcpSocket.connected.connect(this, "onConnect()");
        tcpSocket.error.connect( this, "displayError(com.trolltech.qt.network.QAbstractSocket$SocketError)" );
        // no tcp no delay in Jambi?
        // tcpSocket.setSocketOption(QAbstractSocket.SocketOption.LowDelayOption, 1);

        QGridLayout mainLayout = new QGridLayout();
        mainLayout.addWidget(hostLabel, 0, 0);
        mainLayout.addWidget(hostLineEdit, 0, 1);
        mainLayout.addWidget(portLabel, 1, 0);
        mainLayout.addWidget(portLineEdit, 1, 1);
        mainLayout.addWidget(statusLabel, 2, 0, 1, 2);
        mainLayout.addWidget(buttonBox, 3, 0, 1, 2);
        mainLayout.addWidget(imageLabel, 4, 0, 1, 2);
        setLayout(mainLayout);

        setWindowTitle(tr("Parlevision Test Client"));
        hostLineEdit.setText("localhost");
        portLineEdit.setFocus();
        
        frameBuffer = new byte[1024*1024];
    }
    
    void onConnectButtonPressed()
    {
    	if( tcpSocket.state() != QAbstractSocket.SocketState.ConnectedState )
    	{
	    	connectButton.setEnabled(false);
	        
			tcpSocket.abort();
	        tcpSocket.connectToHost( hostLineEdit.text(), Integer.parseInt( portLineEdit.text() ) );
	             
	        connectButton.setEnabled(false);
	        connectButton.setText(tr("Connecting..."));
    	}
    	else
    	{
    		//disconnect
    		tcpSocket.abort();
    		tcpSocket.disconnectFromHost();
    	}
    }
    
    void onConnect()
    {
    	//connectButton.setEnabled(false);
    	connectButton.setText(tr("Disconnect"));
        connectButton.setEnabled(true);
    }
    
    void onDisconnect()
    {
    	connectButton.setEnabled(false);	
    	connectButton.setText(tr("Connect"));
        connectButton.setEnabled(true);
    }

    void readFrame()
    {
    	//System.out.println("readFrame");
    	
    	QDataStream stream = new QDataStream(tcpSocket);
        stream.setVersion( QDataStream.Version.Qt_4_0.value() );
        
        // first 32-bits tell use the frame size
        if (frameSize == 0) 
        {
            if (tcpSocket.bytesAvailable() < 4 )
                return;
            frameSize = stream.readInt();
        }

        // wait with parsing until complete frame has been received
        if (tcpSocket.bytesAvailable() < frameSize)
            return;
        
        if( frameBuffer.length < frameSize )
        {
        	frameBuffer = new byte[frameSize];
        }
        else
        {
        	for(int i=frameSize; i<frameBuffer.length-frameSize; ++i)
        		frameBuffer[i] = 0;
        }
        stream.readBytes(frameBuffer, frameSize);
        QByteArray a = new QByteArray(frameBuffer);
        parseFrameBuffer(a);
        frameSize = 0;
        
        // the garbage collector stops the application every couple of seconds
        // we call it manually to get better real time performance
        //System.gc();
    }
        
    void parseFrameBuffer(QByteArray buffer) 
    {
    	//System.out.println("parseFrameBuffer");
    	QDataStream stream = new QDataStream(buffer, QIODevice.OpenModeFlag.ReadOnly);
        stream.setVersion( QDataStream.Version.Qt_4_0.value() );

        int type    = stream.readInt();
        int serial  = stream.readInt();
        int numargs = stream.readInt();
        
        if( type == PROTO_FRAME )
        {
	        System.out.println( "Loading frame. Serial: " + serial + " Size: " + frameSize + " Number of args: " + numargs + "." );
	        for( int i=0; i < numargs; ++i )
	        {
		        Object object = loadVariant(stream);
		        
		        if( stream.status() == QDataStream.Status.ReadCorruptData )
		        {
		        	statusLabel.setText( "Datastream corrupt" );
		        	disconnect();
		        	return;
		        }
		        
		        if( object instanceof QImage )
		        {
		        	QImage img = (QImage) object;
		        	imageLabel.setPixmap( QPixmap.fromImage( img ) );
		        }
		        else if(object instanceof CvMatData)
		        {
		        	CvMatData cvmatdata = (CvMatData) object;
		        	if( cvmatdata.isValid() )
		        	{
		        		imageLabel.setPixmap( QPixmap.fromImage( cvmatdata.getImage() ) );
		        		// we need to explicitly free CvMatData after use!
		        		cvmatdata.free();
		        	}
		        }
		        else if( object instanceof String )
		        {
		        	statusLabel.setText( (String)object );
		        }
		        else if( object != null )
		        {
		        	System.out.print( object.getClass().toString() + ":" + object.toString() + " " );
		        }
	        }
        }
        else if(type == PROTO_INIT)
        {
        	System.out.println("PROTO_INIT message not supported.");
        }
        else if(type == PROTO_ACK)
        {
        	System.out.println("PROTO_ACK message not supported.");
        }
        else
        {
        	System.out.println("Unknown message type received.");
        }
        		
        sendAck(serial);
    }
    
    public void sendAck(int serial)
    {
        QByteArray bytes = new QByteArray();
        QDataStream out = new QDataStream(bytes, QIODevice.OpenModeFlag.WriteOnly);
        out.setVersion( QDataStream.Version.Qt_4_0.value() );
        
        // write the header
        out.writeInt(2*4); // size of message excluding 4 bytes for size
        out.writeInt(PROTO_ACK);
        out.writeInt(serial);

        if( tcpSocket.write(bytes) == -1 )
        {
            System.out.println("Error in writing bytes to socket");
        }
        //System.out.println("Sent ack: " + serial );
    }

    void displayError(QAbstractSocket.SocketError socketError)
    {
        switch (socketError) 
        {
        case RemoteHostClosedError:
            break;
        case HostNotFoundError:
            QMessageBox.information(this, tr("ParleVision Client"),
                                     tr("The host was not found. Please check the " +
                                        "host name and port settings."));
            break;
        case ConnectionRefusedError:
            QMessageBox.information(this, tr("ParleVision Client"),
                                     tr("The connection was refused by the peer. " +
                                        "Make sure the ParleVision server is running, " +
                                        "and check that the host name and port " +
                                        "settings are correct."));
            break;
        default:
            QMessageBox.information(this, tr("ParleVision Client"),
            		tr("The following error occurred:") + tcpSocket.errorString());
        }
        
        connectButton.setText("Connect");
        connectButton.setEnabled(true);
    }

    void enableConnectButton()
    {
        String hostStr = hostLineEdit.text();
        String portStr = portLineEdit.text();
    	    	
        if( hostStr != null && portStr != null )
        {
        	connectButton.setEnabled( !(hostLineEdit.text().length() == 0) && !(portLineEdit.text().length() == 0) );
        }
    }

    void sessionOpened()
    {
        statusLabel.setText(tr("This examples requires that you run the ParleVision Server as well."));
        enableConnectButton();
    }
    
    Object loadVariant(QDataStream s)
    {
        Object object = null;
            	
    	int u = s.readInt();
        
        if( s.version() < QDataStream.Version.Qt_4_0.value() ) 
        {
        	s.setStatus(QDataStream.Status.ReadCorruptData);
        }

        //boolean isNull = false;
        if( s.version() >= QDataStream.Version.Qt_4_2.value() )
        {
        	//char is_null = s.readChar();
        	s.readChar();
        	//isNull = (is_null == 1);
        }
        
        switch( u )
        {
	        case QVariant.Image:
	        {
	        	QImage img = new QImage();
	        	img.readFrom(s);
	        	if( !img.isNull() )
	        	{
	        		object = img;
	        	}
	        	break;
	        }
	        case QVariant.Int:
	        {
	        	int i = s.readInt();
	        	object = new Integer(i);
	        	break;
	        }
	        case 3: // uint
	        {
	        	s.readInt();
	        	break;
	        }
	        
	        case QVariant.String:
	        {
	        	String str = s.readString();
	        	object = str;
	        	break;
	        }
	        case QVariant.UserType:
	        {
	        	QByteArray bytearray = new QByteArray();
	        	bytearray.readFrom(s);
	        	String name = bytearray.toString();
	        	System.out.println("Received QVariant of type UserType with name " + name);
            	if( name.equals("plv::CvMatData" ))
            	{
            		CvMatData cvmatdata = new CvMatData();
            		cvmatdata.readFrom(s);
            		object = cvmatdata;
            	}
//            	else if( name.equals("plv::QImageWrapper") )
//            	{
//            		QImageReader reader = new QImageReader(s.device(), new QByteArray("png"));
//            		QImage img = reader.read();
//            		if( img.isNull() )
//            		{
//            			System.out.println("QImageWrapper is invalid");
//            		}
//            		else
//            		{
//            			object = img;
//            		}
//            		int width = s.readInt();
//            		int height = s.readInt();
//            		int format = s.readInt();
//            		int byteCount = s.readInt();
//            		byte[] imgData = new byte[byteCount];
//            		s.readBytes(imgData, byteCount);
//            		QImage img = new QImage(width, height, QImage.Format.resolve(format));
//            		img.loadFromData(imgData);
//            		object = img;
//            	}
            	else
            	{
	            	
		        	System.out.println("Warning: Usertype with name" + name + " is not supported.");
		            s.setStatus(QDataStream.Status.ReadCorruptData);
            	}
	            break;
	        }
	        case QVariant.BitArray:
	        {
	        	QBitArray bitarray = new QBitArray();
	        	bitarray.readFrom(s);
	        	object = bitarray;
	        	break;
	        }
	        case QVariant.Bitmap:
	        {
	        	QBitmap bitmap = new QBitmap();
	        	bitmap.readFrom(s);
	        	object = bitmap;
	        }
	        case QVariant.Boolean:
	        {
	        	boolean b = s.readBoolean();
	        	object = new Boolean(b);
	        }
	        case QVariant.ByteArray:
	        {
	        	QByteArray bytearray = new QByteArray();
	        	bytearray.readFrom(s);
	        	object = bytearray;
	        	break;
	        }
	        case QVariant.Double:
	        {
	        	double d = s.readDouble();
	        	object = new Double(d);
	        	break;
	        }
	        case QVariant.Char:
	        case QVariant.Brush:
	        case QVariant.Color:
	        case QVariant.Cursor:
	        case QVariant.Date:
	        case QVariant.DateTime:
	        case QVariant.Font:
	        case QVariant.Icon:
	        case QVariant.Invalid:
	        case QVariant.KeySequence:
	        case QVariant.Line:
	        case QVariant.LineF:
	        case QVariant.Locale:
	        case QVariant.Long:
	        case QVariant.Palette:
	        case QVariant.Pen:
	        case QVariant.Pixmap:
	        case QVariant.Point:
	        case QVariant.PointF:
	        case QVariant.Polygon:
	        case QVariant.Rect:
	        case QVariant.RectF:
	        case QVariant.RegExp:
	        case QVariant.Region:
	        case QVariant.Size:
	        case QVariant.SizeF:
	        case QVariant.SizePolicy:
	        case QVariant.StringList:
	        case QVariant.TextFormat:
	        case QVariant.TextLength:
	        case QVariant.Time:
	        default:
	        	System.out.println( "Warning, Received QVariant of type " + u + " which is unsupported" );
	        	s.setStatus(QDataStream.Status.ReadCorruptData);
        }
        return object;
    }


    public static void main(String[] args) {
        QApplication.initialize(args);
        
        Client client = new Client(null);
        client.show();

        QApplication.exec();
    }
}
