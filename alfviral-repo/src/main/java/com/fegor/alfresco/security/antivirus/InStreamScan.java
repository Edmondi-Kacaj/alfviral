/*
 * alfviral is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * alfviral is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package com.fegor.alfresco.security.antivirus;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.log4j.Logger;

import com.fegor.alfresco.model.AlfviralModel;

/**
 * InStreamScan
 * 
 * @author fegor
 *
 */
public class InStreamScan implements VirusScanMode {

	private final Logger logger = Logger.getLogger(InStreamScan.class);

	private byte[] data;
	private int chunkSize = 4096;
	private static int port;
	private static String host;
	private int timeout;
	private NodeService nodeService;
	private NodeRef nodeRef;

	/**
	 * Constructor
	 */
	public InStreamScan() {
	}

	/**
	 * Test connection
	 * 
	 * @return test of connection
	 */
	public boolean testConnection() {
		boolean result = true;
		
		logger.info(getClass().getName() + "Testing connect to " + host.toString() + ":" + port);
		Socket socket = new Socket();
		try {
			socket.connect(new InetSocketAddress(host, port));
		} catch (IOException ioe) {
			logger.error(getClass().getName() + "Error connecting to " + host.toString() + ":" + port);
			ioe.printStackTrace();
			result = false;
		} finally {
			if (socket.isConnected()) {
				try {
					socket.close();
				} catch (IOException e) {
					logger.error(getClass().getName() + "Error closing to " + host.toString() + ":" + port);
					e.printStackTrace();
					result = false;
				}
			}
		}
		
		if (result == true) {
			logger.info(getClass().getName() + "Connect to INSTREAM is OK");
		}
		
		return result;
	}
	
	/* (non-Javadoc)
	 * @see com.fegor.alfresco.security.antivirus.VirusScanMode#scan(org.alfresco.service.cmr.repository.NodeRef)
	 */
	@Override
	public int scan(NodeRef nodeRef) {
		int res = 0;
		this.nodeRef = nodeRef;
		try {
			res = scan();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return res;
	}
	
	/*
	 * (non-Javadoc)
	 * 
	 * @see com.fegor.alfresco.security.antivirus.VirusScanMode#scan()
	 */
	@Override
	public int scan() throws IOException {
		int i = 0;
		int result = 0;

		/*
		 * create socket
		 */
		if (logger.isDebugEnabled()) {
			logger.debug(getClass().getName() + "Connect to " + host + ":" + port);
		}
		
		Socket socket = new Socket();
		socket.connect(new InetSocketAddress(host, port));

		try {
			socket.setSoTimeout(timeout);
		} catch (SocketException e) {
			logger.error("Error in timeout: " + timeout + "ms", e);
		}

		DataOutputStream dataOutputStream = null;
		BufferedReader bufferedReader = null;

		String res = null;
		try {
			if (logger.isDebugEnabled()) {
				logger.debug(getClass().getName() + "Send zINSTREAM");
			}
			
			dataOutputStream = new DataOutputStream(socket.getOutputStream());
			dataOutputStream.writeBytes("zINSTREAM\0");

			if (logger.isDebugEnabled()) {
				logger.debug(getClass().getName() + "Send stream for  " + data.length + " bytes");
			}

			while (i < data.length) {
				if (i + chunkSize >= data.length) {
					chunkSize = data.length - i;
				}
				dataOutputStream.writeInt(chunkSize);
				dataOutputStream.write(data, i, chunkSize);
				i += chunkSize;
			}

			dataOutputStream.writeInt(0);
			dataOutputStream.write('\0');
			dataOutputStream.flush();

			bufferedReader = new BufferedReader(new InputStreamReader(
					socket.getInputStream(), "ASCII"));
				
			res = bufferedReader.readLine();
			
			if (logger.isDebugEnabled()) {
				logger.debug(getClass().getName() + "Result of scan is:  " + res);
			}
			
		} finally {
			if (bufferedReader != null)
				bufferedReader.close();
			if (dataOutputStream != null)
				dataOutputStream.close();
			if (socket != null)
				socket.close();
		}

		/*
		 * if is OK then not infected, else, infected...
		 */
		if (!res.trim().equals("stream: OK")) {
			result = 1;
			addAspect();
		}

		return result;
	}

	/*
	 * Re-scanning
	 * 
	 * @see com.fegor.alfresco.security.antivirus.VirusScanMode#rescan()
	 */
	@Override
	public int rescan() throws IOException {
		return scan();
	}

	/*
	 * Report
	 * 
	 * @see com.fegor.alfresco.security.antivirus.VirusScanMode#report()
	 */
	@Override
	public int report() throws IOException {
		int result = 0;
		return result;
	}

	/**
	 * Add aspect Scaned From ClamAV is not assigned
	 */
	private void addAspect() {
		
		if (logger.isDebugEnabled()) {
			logger.debug(getClass().getName() + "Adding aspect if not exist");
		}
		
		if (!nodeService.hasAspect(nodeRef,
				AlfviralModel.ASPECT_SCANNED_FROM_CLAMAV)) {
			nodeService.addAspect(nodeRef,
					AlfviralModel.ASPECT_SCANNED_FROM_CLAMAV, null);
		}

		if (logger.isInfoEnabled()) {
			logger.info(getClass().getName()
					+ ": [Aspect SCANNED_FROM_CLAMAV assigned for "
					+ nodeRef.getId() + "]");
		}

	}

	/**
	 * @param nodeService
	 */
	public void setNodeService(NodeService nodeService) {
		this.nodeService = nodeService;
	}

	/**
	 * @param nodeRef
	 */
	public void setNodeRef(NodeRef nodeRef) {
		this.nodeRef = nodeRef;
	}

	/**
	 * @param data
	 */
	public void setData(byte[] data) {
		this.data = data;
	}

	/**
	 * @param chunkSize
	 */
	public void setChunkSize(int chunkSize) {
		this.chunkSize = chunkSize;
	}

	/**
	 * @param port
	 */
	public void setPort(int port) {
		this.port = port;
	}

	/**
	 * @param host
	 */
	public void setHost(String host) {
		this.host = host;
	}

	/**
	 * @param timeout
	 */
	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

}
