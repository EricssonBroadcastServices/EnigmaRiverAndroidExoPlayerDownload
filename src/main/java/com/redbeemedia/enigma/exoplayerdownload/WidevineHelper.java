package com.redbeemedia.enigma.exoplayerdownload;

import com.redbeemedia.enigma.core.context.EnigmaRiverContext;
import com.redbeemedia.enigma.core.error.EmptyResponseError;
import com.redbeemedia.enigma.core.error.UnexpectedError;
import com.redbeemedia.enigma.core.error.UnexpectedHttpStatusError;
import com.redbeemedia.enigma.core.http.HttpStatus;
import com.redbeemedia.enigma.core.http.IHttpHandler;
import com.redbeemedia.enigma.core.http.SimpleHttpCall;
import com.redbeemedia.enigma.download.resulthandler.IResultHandler;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/*package-protected*/ class WidevineHelper {
    private static final String SCHEME_ID_URI_ATTRIBUTE = "schemeIdUri";

    public static void getManifest(String url, IResultHandler<Document> resultHandler) throws MalformedURLException {
        EnigmaRiverContext.getHttpHandler().doHttp(new URL(url), SimpleHttpCall.GET(), new IHttpHandler.IHttpResponseHandler() {
            @Override
            public void onResponse(HttpStatus httpStatus) {
                resultHandler.onError(new EmptyResponseError("Expected a response."));
            }

            @Override
            public void onResponse(HttpStatus httpStatus, InputStream inputStream) {
                if(!httpStatus.isError()) {
                    Document document;
                    try {
                        document = parseManifest(inputStream);
                    } catch (Exception e) {
                        resultHandler.onError(new UnexpectedError(e));
                        return;
                    }
                    resultHandler.onResult(document);
                    return;
                } else {
                    resultHandler.onError(new UnexpectedHttpStatusError(httpStatus));
                    return;
                }
            }

            @Override
            public void onException(Exception e) {
                resultHandler.onError(new UnexpectedError(e));
            }
        });
    }

    private static Document parseManifest(InputStream inputStream) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder.parse(inputStream);
    }

    public static List<Element> findContentProtectionTags(Element element, UUID schemeUuid) {
        String schemeIdUriValue;
        {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("urn:uuid:");
            stringBuilder.append(schemeUuid.toString().toUpperCase(Locale.ENGLISH));
            schemeIdUriValue = stringBuilder.toString();
        }
        NodeList nodeList = element.getElementsByTagName("ContentProtection");
        List<Element> result = new ArrayList<>();
        for(int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);
            if(node.getNodeType() == Node.ELEMENT_NODE) {
                Element elementNode = ((Element) node);
                if(elementNode.hasAttribute(SCHEME_ID_URI_ATTRIBUTE)) {
                    if(schemeIdUriValue.equals(elementNode.getAttribute(SCHEME_ID_URI_ATTRIBUTE))) {
                        result.add(elementNode);
                    }
                }
            }
        }
        return result;
    }

    public static String getPssh(List<Element> elements) {
        String pssh = null;
        for(Element element : elements) {
            NodeList psshElement = element.getElementsByTagName("cenc:pssh");
            for(int i = 0; i < psshElement.getLength(); ++i) {
                String psshInElement = psshElement.item(i).getTextContent();
                if(pssh == null) {
                    pssh = psshInElement;
                } else {
                    if(psshInElement != null && !psshInElement.equals("")) {
                        if(!pssh.equals(psshInElement)) {
                            throw new RuntimeException("Multiple different pssh found. Not supported.");
                        }
                    }
                }
            }
        }

        if(pssh == null) {
            throw new RuntimeException("No pssh found");
        }

        return pssh;
    }
}
