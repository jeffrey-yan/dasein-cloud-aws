/*
 *  *
 *  Copyright (C) 2009-2015 Dell, Inc.
 *  See annotations for authorship information
 *
 *  ====================================================================
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  ====================================================================
 *
 */

package org.dasein.cloud.aws.compute;

import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.aws.AWSCloud;
import org.dasein.cloud.aws.AwsTestBase;
import org.dasein.cloud.aws.network.ElasticIP;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMFilterOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineLifecycle;
import org.dasein.cloud.compute.VirtualMachineStatus;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VmStatus;
import org.dasein.cloud.compute.VmStatusFilterOptions;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeState;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.collections.Sets;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by Jeffrey Yan on 11/19/2015.
 *
 * @author Jeffrey Yan
 * @since 2016.02.1
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AWSCloud.class, EC2Instance.class, ElasticIP.class})
public class EC2InstanceTest extends AwsTestBase {

    private EC2Instance ec2Instance;

    @Before
    public void setUp() {
        super.setUp();
        ec2Instance = new EC2Instance(awsCloudStub);
    }

    private Document resource(String resourceName) throws Exception {
        DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
        return documentBuilder
                .parse(getClass().getClassLoader().getResourceAsStream(resourceName));
    }

    @Test
    public void testGetPassword() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method ec2MethodStub = mock(EC2Method.class);
        when(ec2MethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/compute/get_password.xml"));

        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("InstanceId", instanceId), hasEntry("Action", "GetPasswordData"))))
                .thenReturn(ec2MethodStub);

        assertEquals("TGludXggdmVyc2lvbiAyLjYuMTYteGVuVSAoYnVpbGRlckBwYXRjaGJhdC5hbWF6b25zYSkgKGdj",
                ec2Instance.getPassword(instanceId));
    }

    @Test
    public void testGetVirtualMachine() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method listIpMethodStub = mock(EC2Method.class);
        when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/describe_addresses.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
                .thenReturn(listIpMethodStub);

        EC2Method getVirtualMachineMethodStub = mock(EC2Method.class);
        when(getVirtualMachineMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub),
                        argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "DescribeInstances"))))
                .thenReturn(getVirtualMachineMethodStub);

        VirtualMachine virtualMachine = ec2Instance.getVirtualMachine(instanceId);
        assertEquals("eipalloc-08229861", virtualMachine.getProviderAssignedIpAddressId());
        assertEquals(Architecture.I64, virtualMachine.getArchitecture());
        assertEquals(VmState.RUNNING, virtualMachine.getCurrentState());
        assertEquals(Platform.WINDOWS,virtualMachine.getPlatform());
        assertEquals("c1.medium", virtualMachine.getProductId());
        assertEquals("us-west-2a", virtualMachine.getProviderDataCenterId());
        assertEquals("ami-1a2b3c4d", virtualMachine.getProviderMachineImageId());
        assertEquals("subnet-1a2b3c4d", virtualMachine.getProviderSubnetId());
        assertEquals("vpc-1a2b3c4d", virtualMachine.getProviderVlanId());
        assertEquals("my-key-pair", virtualMachine.getProviderKeypairId());
        assertEquals(1, virtualMachine.getProviderFirewallIds().length);
        assertEquals("sg-1a2b3c4d", virtualMachine.getProviderFirewallIds()[0]);
        assertEquals(1, virtualMachine.getPublicIpAddresses().length);
        assertEquals("46.51.219.63", virtualMachine.getPublicIpAddresses()[0]);
        assertEquals(ACCOUNT_NO, virtualMachine.getProviderOwnerId());
        assertEquals(REGION, virtualMachine.getProviderRegionId());

        assertEquals(1, virtualMachine.getTags().size());
        assertEquals("Windows Instance", virtualMachine.getTag("Name"));

        Volume volume = virtualMachine.getVolumes()[0];
        assertEquals("/dev/sda1", volume.getDeviceId());
        assertEquals(VolumeState.PENDING, volume.getCurrentState());
        assertTrue(volume.isDeleteOnVirtualMachineTermination());
    }

    @Test
    public void testAlterVirtualMachineProduct() throws Exception {
        String instanceId = "i-2574e22a";
        String targetProductType = "c1.large";

        EC2Method alterVirtualMachineMethodMock = mock(EC2Method.class);
        when(alterVirtualMachineMethodMock.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/modify_instance_attribute.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq("ec2"), eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId", instanceId), hasEntry("InstanceType.Value", targetProductType),
                        hasEntry("Action", "ModifyInstanceAttribute")))).thenReturn(alterVirtualMachineMethodMock);

        EC2Method getVirtualMachineMethodStub = mock(EC2Method.class);
        when(getVirtualMachineMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub),
                        argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "DescribeInstances"))))
                .thenReturn(getVirtualMachineMethodStub);

        ec2Instance.alterVirtualMachineProduct(instanceId, targetProductType);

        verify(alterVirtualMachineMethodMock, times(1)).invoke();
    }

    @Test
    public void testAlterVirtualMachineFirewalls() throws Exception {
        String instanceId = "i-2574e22a";
        String targetFirewall0 = "fwl-1";
        String targetFirewall1 = "fwl-2";

        EC2Method alterVirtualMachineMethodStub = mock(EC2Method.class);
        when(alterVirtualMachineMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/modify_instance_attribute.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq("ec2"), eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId", instanceId), hasEntry("GroupId.0", targetFirewall0),
                        hasEntry("GroupId.1", targetFirewall1), hasEntry("Action", "ModifyInstanceAttribute"))))
                .thenReturn(alterVirtualMachineMethodStub);

        EC2Method describeInstanceMethodStub = mock(EC2Method.class);
        when(describeInstanceMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub),
                        argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "DescribeInstances"))))
                .thenReturn(describeInstanceMethodStub);

        ec2Instance.alterVirtualMachineFirewalls(instanceId, new String[] { targetFirewall0, targetFirewall1 });

        verify(alterVirtualMachineMethodStub, times(1)).invoke();
    }

    @Test
    public void testGetUserData() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method getUserDataMethodStub = mock(EC2Method.class);
        when(getUserDataMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/compute/get_userdata.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId", instanceId), hasEntry("Attribute", "userData"),
                        hasEntry("Action", "DescribeInstanceAttribute")))).thenReturn(getUserDataMethodStub);

        assertEquals("exmple_user_data", ec2Instance.getUserData(instanceId));
    }

    @Test
    public void testGetConsoleOutput() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method getUserDataMethodStub = mock(EC2Method.class);
        when(getUserDataMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/compute/get_consoleoutput.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId", instanceId), hasEntry("Action", "GetConsoleOutput"))))
                .thenReturn(getUserDataMethodStub);

        String consoleOutput = ec2Instance.getConsoleOutput(instanceId);
        assertTrue(consoleOutput.startsWith("Linux version 2.6.16-xenU"));
    }

    @Test
    public void testListFirewalls() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method describeInstanceMethodStub = mock(EC2Method.class);
        when(describeInstanceMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub),
                        argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "DescribeInstances"))))
                .thenReturn(describeInstanceMethodStub);

        List<String> firewalls = (List<String>) ec2Instance.listFirewalls(instanceId);
        assertEquals(3, firewalls.size());
        assertEquals("sg-1a2b3c4d", firewalls.get(0));
    }

    @Test
    public void testGetVMStatus() throws Exception {
        String instanceId1 = "i-2574e22a";
        String instanceId2 = "i-2a2b3c4d";
        VmStatus status = VmStatus.IMPAIRED;

        EC2Method describeInstanceStatusMethodStub = mock(EC2Method.class);
        when(describeInstanceStatusMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance_status.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId.1", instanceId1), hasEntry("InstanceId.2", instanceId2),
                        hasEntry("Filter.0.Name", "system-status.status"), hasEntry("Filter.0.Value.0", "impaired"),
                        hasEntry("Filter.1.Name", "instance-status.status"), hasEntry("Filter.1.Value.0", "impaired"),
                        hasEntry("Action", "DescribeInstanceStatus")))).thenReturn(describeInstanceStatusMethodStub);

        List<VirtualMachineStatus> vmStatus = (List<VirtualMachineStatus>) ec2Instance.getVMStatus(
                VmStatusFilterOptions.getInstance().withVmIds(instanceId1, instanceId2)
                        .withVmStatuses(Sets.newSet(VmStatus.IMPAIRED)));

        assertEquals(1, vmStatus.size());
        assertEquals(instanceId1, vmStatus.get(0).getProviderVirtualMachineId());
        assertEquals(status, vmStatus.get(0).getProviderHostStatus());
        assertEquals(status, vmStatus.get(0).getProviderVmStatus());
    }

    @Test
    public void testListVirtualMachineStatus() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method describeInstanceMethodStub = mock(EC2Method.class);
        when(describeInstanceMethodStub.invoke()).thenReturn(
                resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeInstances"))))
                .thenReturn(describeInstanceMethodStub);

        List<ResourceStatus> resourceStatuses = (List<ResourceStatus>) ec2Instance.listVirtualMachineStatus();
        assertEquals(1, resourceStatuses.size());
        assertEquals(instanceId, resourceStatuses.get(0).getProviderResourceId());
        assertEquals(VmState.RUNNING, resourceStatuses.get(0).getResourceStatus());
    }

    @Test
    public void testListVirtualMachines() throws Exception {
        EC2Method listIpMethodStub = mock(EC2Method.class);
        when(listIpMethodStub.invoke()).thenReturn(resource("org/dasein/cloud/aws/network/describe_addresses.xml"));
        PowerMockito.whenNew(EC2Method.class)
                .withArguments(eq(awsCloudStub), argThat(allOf(hasEntry("Action", "DescribeAddresses"))))
                .thenReturn(listIpMethodStub);


        String spotRequestId = "spot-1a2b3c4d";
        String tagKey = "tk-1a2b3c4d";
        String tagValue = "tv-1a2b3c4d";

        Map<String, String> tags = new HashMap<>();
        tags.put(tagKey, tagValue);


        EC2Method describeInstanceMethodStub = mock(EC2Method.class);
        when(describeInstanceMethodStub.invoke())
                .thenReturn(resource("org/dasein/cloud/aws/compute/describe_instance.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("Filter.0.Name", "tag:" + tagKey), hasEntry("Filter.0.Value.0", tagValue),
                        hasEntry("Filter.1.Name", "instance-state-name"), hasEntry("Filter.1.Value.0", "running"),
                        hasEntry("Filter.2.Name", "instance-lifecycle"), hasEntry("Filter.2.Value.0", "spot"),
                        hasEntry("Filter.3.Name", "spot-instance-request-id"), hasEntry("Filter.3.Value.0", spotRequestId),
                        hasEntry("Action", "DescribeInstances"))))
                .thenReturn(describeInstanceMethodStub);

        List<VirtualMachine> virtualMachines = (List<VirtualMachine>) ec2Instance.listVirtualMachines(
                VMFilterOptions.getInstance().withVmStates(Sets.newSet(VmState.RUNNING))
                        .withLifecycles(VirtualMachineLifecycle.SPOT).withSpotRequestId(spotRequestId).withTags(tags));

        assertEquals(1, virtualMachines.size());
    }

    @Test
    public void testEnableAnalytics() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method monitorInstanceMock = mock(EC2Method.class);
        when(monitorInstanceMock.invoke()).thenReturn(resource("org/dasein/cloud/aws/compute/monitor_instance.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "MonitorInstances"))))
                .thenReturn(monitorInstanceMock);

        ec2Instance.enableAnalytics(instanceId);

        verify(monitorInstanceMock, times(1)).invoke();
    }

    @Test
    public void testDisableAnalytics() throws Exception {
        String instanceId = "i-2574e22a";

        EC2Method unmonitorInstanceMock = mock(EC2Method.class);
        when(unmonitorInstanceMock.invoke()).thenReturn(resource("org/dasein/cloud/aws/compute/unmonitor_instance.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(awsCloudStub),
                argThat(allOf(hasEntry("InstanceId.1", instanceId), hasEntry("Action", "UnmonitorInstances"))))
                .thenReturn(unmonitorInstanceMock);

        ec2Instance.disableAnalytics(instanceId);

        verify(unmonitorInstanceMock, times(1)).invoke();
    }

    //getVMStatistics not tested, as no sample response data
}
