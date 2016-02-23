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

import org.dasein.cloud.aws.AWSCloud;
import org.dasein.cloud.aws.AwsTestBase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.w3c.dom.Document;

import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.core.AllOf.allOf;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Created by Jeffrey Yan on 2/23/2016.
 *
 * @author Jeffrey Yan
 * @since 2016.02.1
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest(value = { AWSCloud.class, AutoScaling.class }, fullyQualifiedNames = "org.dasein.cloud.aws.compute.AutoScaling*")
public class AutoScalingTest extends AwsTestBase {

    private static final String AUTO_SCALLING_SERVICE_ID = "autoscaling";


    private AutoScaling autoScaling;

    @Before
    public void setUp() throws Exception {
        super.setUp();

        autoScaling = spy(new AutoScaling(awsCloudStub));
        EC2ComputeServices computeServices = mock(EC2ComputeServices.class);
        doReturn(computeServices).when(awsCloudStub).getComputeServices();
    }

    protected Document resource(String resourceName) throws Exception {
        return super.resource("org/dasein/cloud/aws/compute/auto_scaling/" + resourceName);
    }

    @Test
    public void testIsSubscribed() throws Exception {
        EC2Method describeAutoScalingGroupsStub = mock(EC2Method.class);
        when(describeAutoScalingGroupsStub.invoke()).thenReturn(resource("describe_auto_scaling_groups_single.xml"));
        PowerMockito.whenNew(EC2Method.class).withArguments(eq(AUTO_SCALLING_SERVICE_ID), eq(awsCloudStub),
                argThat(allOf(hasEntry("Action", "DescribeAutoScalingGroups"))))
                .thenReturn(describeAutoScalingGroupsStub);

        assertTrue(autoScaling.isSubscribed());
    }
}
