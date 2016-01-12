package org.dasein.cloud.aws.network;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.AllOf.allOf;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aws.AWSCloud;
import org.dasein.cloud.aws.AwsTestBase;
import org.dasein.cloud.aws.compute.EC2Exception;
import org.dasein.cloud.aws.compute.EC2Method;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;

import static org.junit.Assert.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({AWSCloud.class, ElasticIP.class})
public class ElasticIPTest extends AwsTestBase {

	private ElasticIP elasticIP;
	
	@Before
	public void setUp() {
		super.setUp();
		elasticIP = new ElasticIP(awsCloudStub);
	}
	
	private Document resource(String resourceName) throws Exception {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder
                .parse(getClass().getClassLoader().getResourceAsStream(resourceName));
    }
	
	@Test
	public void getIpAddressShouldReturnVpcAddress() throws Exception {
		
		EC2Method listIpMethodStub = Mockito.mock(EC2Method.class);
        Mockito.when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/list_ip.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
                .thenReturn(listIpMethodStub);
		
		IpAddress ipAddress = elasticIP.getIpAddress("eipalloc-08229861");
		assertEquals("eipalloc-08229861", ipAddress.getProviderIpAddressId());
		assertEquals("eipassoc-f0229899", ipAddress.getProviderAssociationId());
		assertEquals(AddressType.PUBLIC, ipAddress.getAddressType());				
		assertEquals("eni-ef229886", ipAddress.getProviderNetworkInterfaceId());
		assertEquals(IPVersion.IPV4, ipAddress.getVersion());
		assertEquals("46.51.219.63", ipAddress.getRawAddress().getIpAddress());
		assertEquals(REGION, ipAddress.getRegionId());
		assertEquals(true, ipAddress.isForVlan());
		assertEquals("i-64600030", ipAddress.getServerId());
		assertEquals(true, ipAddress.isAssigned());
	}
	
	@Test
	public void getIpAddressShouldReturnIpAddress() throws EC2Exception, CloudException, InternalException, Exception {
		
		EC2Method listIpMethodStub = Mockito.mock(EC2Method.class);
        Mockito.when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/list_ip.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
                .thenReturn(listIpMethodStub);
		
		IpAddress ipAddress = elasticIP.getIpAddress("46.51.219.64");
		assertEquals("eipassoc-f0229810", ipAddress.getProviderAssociationId());
		assertEquals("46.51.219.64", ipAddress.getRawAddress().getIpAddress());
		assertEquals("eni-ef229810", ipAddress.getProviderNetworkInterfaceId());
		assertEquals(REGION, ipAddress.getRegionId());
		assertEquals("i-64600031", ipAddress.getServerId());
		assertEquals(AddressType.PUBLIC, ipAddress.getAddressType());
		assertEquals(IPVersion.IPV4, ipAddress.getVersion());
		assertEquals("46.51.219.64", ipAddress.getProviderIpAddressId());
		assertEquals(false, ipAddress.isForVlan());
		assertEquals(true, ipAddress.isAssigned());	//TODO check
	}
	
	@Test
	public void listIpPoolShouldReturnCorrectResult() throws EC2Exception, CloudException, InternalException, Exception {
		
		EC2Method listIpMethodStub = Mockito.mock(EC2Method.class);
		Mockito.when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/list_ip.xml"));
		PowerMockito.whenNew(EC2Method.class)
				.withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
				.thenReturn(listIpMethodStub);
		
		Iterable<IpAddress> ipAddresses = elasticIP.listIpPool(IPVersion.IPV4, false);
		Iterator<IpAddress> iter = ipAddresses.iterator();
		int count = 0;
		while (iter.hasNext()) {
			count++;
			IpAddress ipAddress = iter.next();
			if (count == 1) {
				assertEquals("eipalloc-08229861", ipAddress.getProviderIpAddressId());
				assertEquals("eipassoc-f0229899", ipAddress.getProviderAssociationId());
				assertEquals("eni-ef229886", ipAddress.getProviderNetworkInterfaceId());
				assertEquals("46.51.219.63", ipAddress.getRawAddress().getIpAddress());
				assertEquals(true, ipAddress.isForVlan());
				assertEquals("i-64600030", ipAddress.getServerId());
				assertEquals(true, ipAddress.isAssigned());
			} else if (count == 2) {
				assertEquals("eipassoc-f0229810", ipAddress.getProviderAssociationId());
				assertEquals("46.51.219.64", ipAddress.getRawAddress().getIpAddress());
				assertEquals("eni-ef229810", ipAddress.getProviderNetworkInterfaceId());
				assertEquals("i-64600031", ipAddress.getServerId());
				assertEquals("46.51.219.64", ipAddress.getProviderIpAddressId());
				assertEquals(false, ipAddress.isForVlan());
				assertEquals(true, ipAddress.isAssigned());
			} else if (count == 3) {
				assertEquals("198.51.100.2", ipAddress.getProviderIpAddressId());
				assertEquals("198.51.100.2", ipAddress.getRawAddress().getIpAddress());
				assertEquals(false, ipAddress.isForVlan());
				assertEquals(false, ipAddress.isAssigned());
			}
			assertEquals(REGION, ipAddress.getRegionId());
			assertEquals(AddressType.PUBLIC, ipAddress.getAddressType());
			assertEquals(IPVersion.IPV4, ipAddress.getVersion());
		}
		assertEquals(3, count);
	}
	
	@Test
	public void listIpPoolStatusShouldReturnCorrectResult() throws Exception {
		
		EC2Method listIpMethodStub = Mockito.mock(EC2Method.class);
		Mockito.when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/list_ip.xml"));
		PowerMockito.whenNew(EC2Method.class)
				.withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
				.thenReturn(listIpMethodStub);
		
		Iterable<ResourceStatus> resourceStatuses = elasticIP.listIpPoolStatus(IPVersion.IPV4);
		Iterator<ResourceStatus> iter = resourceStatuses.iterator();
		int count = 0;
		while (iter.hasNext()) {
			count++;
			ResourceStatus resourceStatus = iter.next();
			if (count == 1) {
				assertEquals("eipalloc-08229861", resourceStatus.getProviderResourceId());
				assertEquals(false, resourceStatus.getResourceStatus());
			} else if (count == 2) {
				assertEquals("46.51.219.64", resourceStatus.getProviderResourceId());
				assertEquals(false, resourceStatus.getResourceStatus());
			} else if (count == 3) {
				assertEquals("198.51.100.2", resourceStatus.getProviderResourceId());
				assertEquals(true, resourceStatus.getResourceStatus());
			}
		}
		assertEquals(3, count);
	}
	
	@Test
	public void listRulesShouldReturnCorrectResult() throws InternalException, CloudException {
		assertEquals(Collections.emptyList(), elasticIP.listRules(null));
	}
	
	
	
}
