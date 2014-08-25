package xdi2.connect.buttonbuilder;

import java.io.IOException;
import java.io.StringWriter;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.interfaces.RSAKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;

import xdi2.core.ContextNode;
import xdi2.core.Graph;
import xdi2.core.constants.XDILinkContractConstants;
import xdi2.core.features.linkcontracts.template.LinkContractTemplate;
import xdi2.core.features.nodetypes.XdiAbstractVariable;
import xdi2.core.features.policy.PolicyAnd;
import xdi2.core.features.policy.PolicyRoot;
import xdi2.core.features.policy.PolicyUtil;
import xdi2.core.features.signatures.KeyPairSignature;
import xdi2.core.features.signatures.Signatures;
import xdi2.core.impl.memory.MemoryGraphFactory;
import xdi2.core.io.XDIWriter;
import xdi2.core.io.XDIWriterRegistry;
import xdi2.core.io.writers.XDIJSONWriter;
import xdi2.core.syntax.XDIAddress;
import xdi2.core.syntax.XDIStatement;

public class BuildLinkContractTemplateXDI extends javax.servlet.http.HttpServlet implements javax.servlet.Servlet {

	private static final long serialVersionUID = -2033109040103002340L;

	public static final XDIAddress TO_PEER_ROOT_XRI = XDIAddress.create("{$to}");
	public static final XDIAddress MESSAGE_TYPE = XDIAddress.create("$connect[$v]#0$xdi[$v]#1$msg");
	public static final XDIAddress OPERATION_XRI = XDIAddress.create("$set{$do}");

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		this.doPost(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		String requestingPartyString = request.getParameter("requestingParty");
		String linkContractTemplateAddressString = request.getParameter("linkContractTemplateAddress");
		String privateKeyString = request.getParameter("privateKey");
		String requestCloudNameString = request.getParameter("requestCloudName");
		String requestAttributesString = request.getParameter("requestAttributes");

		// set up parameters

		if (requestingPartyString == null || requestingPartyString.trim().isEmpty()) throw new ServletException("No requesting party.");
		if (linkContractTemplateAddressString == null || linkContractTemplateAddressString.trim().isEmpty()) throw new ServletException("No link contract template address.");
		if (privateKeyString == null || privateKeyString.trim().isEmpty()) throw new ServletException("No private key.");

		XDIAddress requestingParty = XDIAddress.create(requestingPartyString);

		XDIAddress linkContractTemplateAddress = XDIAddress.create(linkContractTemplateAddressString);

		boolean requestCloudName = "on".equals(requestCloudNameString);

		String[] requestAttributesStrings = requestAttributesString.split("\n");
		List<XDIAddress> requestAttributes = new ArrayList<XDIAddress> ();

		for (int i=0; i<requestAttributesStrings.length; i++) {

			requestAttributesStrings[i] = requestAttributesStrings[i].trim();
			if (requestAttributesStrings[i].isEmpty()) continue;

			requestAttributes.add(XDIAddress.create(requestAttributesStrings[i]));
		}

		PrivateKey privateKey;		

		try {

			PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(Base64.decodeBase64(privateKeyString));
			KeyFactory keyFactory = KeyFactory.getInstance("RSA");
			privateKey = keyFactory.generatePrivate(keySpec);
		} catch (GeneralSecurityException ex) {

			throw new ServletException(ex.getMessage(), ex);
		}

		// create link contract template XDI

		Graph graph = MemoryGraphFactory.getInstance().openGraph();
		ContextNode linkContractTemplateContextNode = graph.setDeepContextNode(linkContractTemplateAddress);

		LinkContractTemplate linkContractTemplate = LinkContractTemplate.fromXdiVariable(XdiAbstractVariable.fromContextNode(linkContractTemplateContextNode));

		PolicyRoot policyRoot = linkContractTemplate.getPolicyRoot(true);
		PolicyAnd policyAnd = policyRoot.createAndPolicy(true);

		PolicyUtil.createSenderIsOperator(policyAnd, requestingParty);
		PolicyUtil.createSignatureValidOperator(policyAnd);

		for (XDIAddress requestAttribute : requestAttributes) linkContractTemplate.setPermissionTargetXDIAddress(XDILinkContractConstants.XDI_ADD_GET, requestAttribute);

		if (requestCloudName) linkContractTemplate.setPermissionTargetXDIStatement(XDILinkContractConstants.XDI_ADD_GET, XDIStatement.create("{$to}/$is$ref/{}"));

		// sign

		try {

			((KeyPairSignature) Signatures.createSignature(linkContractTemplate.getContextNode(), "sha", 256, "rsa", ((RSAKey) privateKey).getModulus().bitLength(), false)).sign(privateKey);
		} catch (GeneralSecurityException ex) {

			throw new ServletException(ex.getMessage(), ex);
		}

		// output it

		Properties parameters = new Properties();
		parameters.setProperty(XDIWriterRegistry.PARAMETER_PRETTY, "1");
		XDIWriter xdiWriter = XDIWriterRegistry.forFormat("XDI/JSON", parameters);
		StringWriter buffer = new StringWriter();
		xdiWriter.write(graph, buffer);
		response.setContentType(XDIJSONWriter.MIME_TYPE.getMimeType());
		response.getWriter().write(buffer.getBuffer().toString());
	}   	  	    
}
