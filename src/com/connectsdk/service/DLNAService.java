/*
 * DLNAService
 * Connect SDK
 * 
 * Copyright (c) 2014 LG Electronics. All rights reserved.
 * Created by Hyun Kook Khang on 19 Jan 2014
 * 
 */

package com.connectsdk.service;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import com.connectsdk.core.Util;
import com.connectsdk.core.upnp.service.Service;
import com.connectsdk.device.ConnectableDeviceStore;
import com.connectsdk.service.capability.MediaControl;
import com.connectsdk.service.capability.MediaPlayer;
import com.connectsdk.service.capability.listeners.ResponseListener;
import com.connectsdk.service.command.ServiceCommand;
import com.connectsdk.service.command.ServiceCommandError;
import com.connectsdk.service.command.ServiceSubscription;
import com.connectsdk.service.config.ServiceConfig;
import com.connectsdk.service.config.ServiceDescription;
import com.connectsdk.service.sessions.LaunchSession;
import com.connectsdk.service.sessions.LaunchSession.LaunchSessionType;

public class DLNAService extends DeviceService implements MediaControl, MediaPlayer {
	private static final String DATA = "XMLData";
	private static final String ACTION = "SOAPAction";
	private static final String	ACTION_CONTENT = "\"urn:schemas-upnp-org:service:AVTransport:1#%s\"";

	String controlURL;
	
	interface PositionInfoListener {
		public void onGetPositionInfoSuccess(String positionInfoXml);
		public void onGetPositionInfoFailed(ServiceCommandError error);
	}
	
	public DLNAService(ServiceDescription serviceDescription, ServiceConfig serviceConfig, ConnectableDeviceStore connectableDeviceStore) {
		super(serviceDescription, serviceConfig, connectableDeviceStore);
		
		setCapabilities();
		
		StringBuilder sb = new StringBuilder();
		List<Service> serviceList = serviceDescription.getServiceList();

		if ( serviceList != null ) {
			for ( int i = 0; i < serviceList.size(); i++) {
				if ( serviceList.get(i).controlURL.contains("AVTransport") ) {
					sb.append(serviceList.get(i).baseURL);
					sb.append(serviceList.get(i).controlURL);
					break;
				}
			}
			controlURL = sb.toString();
		}
	}
	
	public static JSONObject discoveryParameters() {
		JSONObject params = new JSONObject();
		
		try {
			params.put("serviceId", "DLNA");
			params.put("filter",  "urn:schemas-upnp-org:device:MediaRenderer:1");
		} catch (JSONException e) {
			e.printStackTrace();
		}

		return params;
	}
	
	/******************
    MEDIA PLAYER
    *****************/
	@Override
	public MediaPlayer getMediaPlayer() {
		return this;
	};
	
	@Override
	public CapabilityPriorityLevel getMediaPlayerCapabilityLevel() {
		return CapabilityPriorityLevel.NORMAL;
	}
	
	public void displayMedia(final String url, final String mimeType, final String title, final String description, final String iconSrc, final LaunchListener listener) {
		stop(new ResponseListener<Object>() {
			
			@Override
			public void onSuccess(Object response) {
				final String instanceId = "0";
				
				ResponseListener<Object> responseListener = new ResponseListener<Object>() {
					
					@Override
					public void onSuccess(Object response) {
						String method = "Play";
						
						Map<String, String> parameters = new HashMap<String, String>();
						parameters.put("Speed", "1");
						
						JSONObject payload = getMethodBody(instanceId, method, parameters);
						
						ResponseListener<Object> playResponseListener = new ResponseListener<Object> () {
							@Override
							public void onSuccess(Object response) {
								LaunchSession launchSession = new LaunchSession();
								launchSession.setService(DLNAService.this);
								launchSession.setSessionType(LaunchSessionType.Media);

								Util.postSuccess(listener, new MediaLaunchObject(launchSession, DLNAService.this));
							}
							
							@Override
							public void onError(ServiceCommandError error) {
								if ( listener != null ) {
									listener.onError(error);
								}
							}
						};
					
						ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(DLNAService.this, controlURL, payload, playResponseListener);
						request.send();
					}
					
					@Override
					public void onError(ServiceCommandError error) {
						if ( listener != null ) {
							listener.onError(error);
						}
					}
				};

				String method = "SetAVTransportURI";
		        String httpMessage = getSetAVTransportURIBody(instanceId, url, mimeType, title);

				ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(DLNAService.this, controlURL, httpMessage, responseListener);
				request.send();				
			}
			
			@Override
			public void onError(ServiceCommandError error) {
				if (listener != null) {
					listener.onError(error);
				}
			}
		});
	}
	
	@Override
	public void displayImage(String url, String mimeType, String title, String description, String iconSrc, LaunchListener listener) {
		displayMedia(url, mimeType, title, description, iconSrc, listener);
	}
	
	@Override
	public void playMedia(String url, String mimeType, String title, String description, String iconSrc, boolean shouldLoop, LaunchListener listener) {
		displayMedia(url, mimeType, title, description, iconSrc, listener);
	}
	
	@Override
	public void closeMedia(LaunchSession launchSession, ResponseListener<Object> listener) {
		if (launchSession.getService() instanceof DLNAService)
			((DLNAService) launchSession.getService()).stop(listener);
	}
	
	/******************
    MEDIA CONTROL
    *****************/
	@Override
	public MediaControl getMediaControl() {
		return this;
	};
	
	@Override
	public CapabilityPriorityLevel getMediaControlCapabilityLevel() {
		return CapabilityPriorityLevel.NORMAL;
	}

	@Override
	public void play(final ResponseListener<Object> listener) {
	  	String method = "Play";
		String instanceId = "0";

		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("Speed", "1");
		
		JSONObject payload = getMethodBody(instanceId, method, parameters);

		ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(this, controlURL, payload, listener);
		request.send();
	}

	@Override
	public void pause(final ResponseListener<Object> listener) {
    	String method = "Pause";
		String instanceId = "0";

		JSONObject payload = getMethodBody(instanceId, method);

		ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(this, controlURL, payload, listener);
		request.send();
	}

	@Override
	public void stop(final ResponseListener<Object> listener) {
    	String method = "Stop";
		String instanceId = "0";

		JSONObject payload = getMethodBody(instanceId, method);

		ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(this, controlURL, payload, listener);
		request.send();
	}
	
	@Override
	public void rewind(final ResponseListener<Object> listener) {
		Util.postError(listener, ServiceCommandError.notSupported());
	}

	@Override
	public void fastForward(final ResponseListener<Object> listener) {
		Util.postError(listener, ServiceCommandError.notSupported());
	}
	
	@Override
	public void seek(long position, ResponseListener<Object> listener) {
    	String method = "Seek";
		String instanceId = "0";
		
		long second = (position / 1000) % 60;
		long minute = (position / (1000 * 60)) % 60;
		long hour = (position / (1000 * 60 * 60)) % 24;

		String time = String.format(Locale.US, "%02d:%02d:%02d", hour, minute, second);
		
		Map<String, String> parameters = new HashMap<String, String>();
		parameters.put("Unit", "REL_TIME");
		parameters.put("Target", time);

		JSONObject payload = getMethodBody(instanceId, method, parameters);

		ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(this, controlURL, payload, listener);
		request.send();
	}
	
	private void getPositionInfo(final PositionInfoListener listener) {
    	String method = "GetPositionInfo";
		String instanceId = "0";

		JSONObject payload = getMethodBody(instanceId, method);
		
		ResponseListener<Object> responseListener = new ResponseListener<Object>() {
			
			@Override
			public void onSuccess(Object response) {
				
				if (listener != null) {
					listener.onGetPositionInfoSuccess((String)response);
				}
			}
			
			@Override
			public void onError(ServiceCommandError error) {
				if (listener != null) {
					listener.onGetPositionInfoFailed(error);
				}
			}
		};

		ServiceCommand<ResponseListener<Object>> request = new ServiceCommand<ResponseListener<Object>>(this, controlURL, payload, responseListener);
		request.send();
	}
	
	@Override
	public void getDuration(final DurationListener listener) {
		getPositionInfo(new PositionInfoListener() {
			
			@Override
			public void onGetPositionInfoSuccess(String positionInfoXml) {
				String strDuration = parseData(positionInfoXml, "TrackDuration");
				
				long milliTimes = convertStrTimeFormatToLong(strDuration) * 1000;
				
				if (listener != null) {
					listener.onSuccess(milliTimes);
				}
			}
			
			@Override
			public void onGetPositionInfoFailed(ServiceCommandError error) {
				if (listener != null) {
					listener.onError(error);
				}
			}
		});
	}
	
	@Override
	public void getPosition(final PositionListener listener) {
		getPositionInfo(new PositionInfoListener() {
			
			@Override
			public void onGetPositionInfoSuccess(String positionInfoXml) {
				String strDuration = parseData(positionInfoXml, "RelTime");
				
				long milliTimes = convertStrTimeFormatToLong(strDuration) * 1000;
				
				if (listener != null) {
					listener.onSuccess(milliTimes);
				}
			}
			
			@Override
			public void onGetPositionInfoFailed(ServiceCommandError error) {
				if (listener != null) {
					listener.onError(error);
				}
			}
		});
	}

	private String getSetAVTransportURIBody(String instanceId, String mediaURL, String mime, String title) { 
		String action = "SetAVTransportURI";
		String metadata = getMetadata(mediaURL, mime, title);
		
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">");

        sb.append("<s:Body>");
        sb.append("<u:" + action + " xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">");
        sb.append("<InstanceID>" + instanceId + "</InstanceID>");
        sb.append("<CurrentURI>" + mediaURL + "</CurrentURI>");
        sb.append("<CurrentURIMetaData>"+ metadata + "</CurrentURIMetaData>");
        
        sb.append("</u:" + action + ">");
        sb.append("</s:Body>");
        sb.append("</s:Envelope>");

        return sb.toString();
	}
	
	private JSONObject getMethodBody(String instanceId, String method) {
		return getMethodBody(instanceId, method, null);
	}
	
	private JSONObject getMethodBody(String instanceId, String method, Map<String, String> parameters) {
        StringBuilder sb = new StringBuilder();
        
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<s:Envelope s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\" xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\">");
        
        sb.append("<s:Body>");
        sb.append("<u:" + method + " xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">");
        sb.append("<InstanceID>" + instanceId + "</InstanceID>");
        
        if (parameters != null) {
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                
                sb.append("<" + key + ">");
                sb.append(value);
                sb.append("</" + key + ">");
            }
        }
        
        sb.append("</u:" + method + ">");
        sb.append("</s:Body>");
        sb.append("</s:Envelope>");

        JSONObject obj = new JSONObject();
        try {
			obj.put(DATA, sb.toString());
			obj.put(ACTION, String.format(ACTION_CONTENT, method));
		} catch (JSONException e) {
			e.printStackTrace();
		}

        return obj;
	}
	
	private String getMetadata(String mediaURL, String mime, String title) {
		String id = "1000";
		String parentID = "0";
		String restricted = "0";
		String objectClass = null;
		StringBuilder sb = new StringBuilder();

		sb.append("&lt;DIDL-Lite xmlns=&quot;urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/&quot; ");
		sb.append("xmlns:upnp=&quot;urn:schemas-upnp-org:metadata-1-0/upnp/&quot; ");
		sb.append("xmlns:dc=&quot;http://purl.org/dc/elements/1.1/&quot;&gt;");

		sb.append("&lt;item id=&quot;" + id + "&quot; parentID=&quot;" + parentID + "&quot; restricted=&quot;" + restricted + "&quot;&gt;");
		sb.append("&lt;dc:title&gt;" + title + "&lt;/dc:title&gt;");
		
		if ( mime.startsWith("image") ) {
			objectClass = "object.item.imageItem";
		}
		else if ( mime.startsWith("video") ) {
			objectClass = "object.item.videoItem";
		}
		else if ( mime.startsWith("audio") ) {
			objectClass = "object.item.audioItem";
		}
		sb.append("&lt;res protocolInfo=&quot;http-get:*:" + mime + ":DLNA.ORG_OP=01&quot;&gt;" + mediaURL + "&lt;/res&gt;");
		sb.append("&lt;upnp:class&gt;" + objectClass + "&lt;/upnp:class&gt;");

		sb.append("&lt;/item&gt;");
		sb.append("&lt;/DIDL-Lite&gt;");
		
		return sb.toString();
	}
	
	@Override
	public void sendCommand(final ServiceCommand<?> mCommand) {
		Util.runInBackground(new Runnable() {
			
			@SuppressWarnings("unchecked")
			@Override
			public void run() {
				ServiceCommand<ResponseListener<Object>> command = (ServiceCommand<ResponseListener<Object>>) mCommand;
				HttpClient httpClient = new DefaultHttpClient();
				
				JSONObject payload = (JSONObject) command.getPayload();
			
				HttpPost request = (HttpPost) command.getRequest();
				request.setHeader(ACTION, payload.optString(ACTION));
				try {
					request.setEntity(new StringEntity(command.getPayload().toString()));
				} catch (UnsupportedEncodingException e) {
					e.printStackTrace();
				}
				
				HttpResponse response = null;

				try {
					response = httpClient.execute(request);
	
					final int code = response.getStatusLine().getStatusCode();
					
					if ( code == 200 ) { 
			            HttpEntity entity = response.getEntity();
			            final String message = EntityUtils.toString(entity, "UTF-8");
			            
						Util.postSuccess(command.getResponseListener(), message);
					}
					else {
						Util.postError(command.getResponseListener(), ServiceCommandError.getError(code));
					}
		
					response.getEntity().consumeContent();
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
	}
	
	private void setCapabilities() {
		appendCapabilites(
				Display_Image, 
				Display_Video, 
				Play, 
				MetaData_Title, 
				MetaData_MimeType, 
				Duration, 
				Position, 
				Seek
		);
	}
	
	@Override
	public LaunchSession decodeLaunchSession(String type, JSONObject sessionObj) throws JSONException {
		if (type == "dlna") {
			LaunchSession launchSession = LaunchSession.launchSessionFromJSONObject(sessionObj);
			launchSession.setService(this);

			return launchSession;
		}
		return null;
	}
	
	private String parseData(String response, String key) {
		String startTag = "<" + key + ">";
		String endTag = "</" + key + ">";
		
		int start = response.indexOf(startTag);
		int end = response.indexOf(endTag);
		
		String data = response.substring(start + startTag.length(), end);
		
		return data;
	}
	
	private long convertStrTimeFormatToLong(String strTime) {
		String[] tokens = strTime.split(":");
		long time = 0;
		
		for (int i = 0; i < tokens.length; i++) {
			time *= 60;
			time += Integer.parseInt(tokens[i]);
		}
		
		return time;
	}

	@Override
	public void getPlayState(PlayStateListener listener) {
		if (listener != null)
			listener.onError(ServiceCommandError.notSupported());
	}

	@Override
	public ServiceSubscription<PlayStateListener> subscribePlayState(PlayStateListener listener) {
		if (listener != null)
			listener.onError(ServiceCommandError.notSupported());

		return null;
	}
}