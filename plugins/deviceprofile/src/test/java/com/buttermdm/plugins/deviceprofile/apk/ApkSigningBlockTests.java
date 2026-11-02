/*
 *
 * ButterMDM: Open Source Android MDM Software
 * ButterMDM (https://github.com/ButterMDM)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.buttermdm.plugins.deviceprofile.apk;

import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.security.MessageDigest;

/**
 * <p>Tests of {@link ApkSigningBlock}. The expected digests were printed by <code>apksigner verify --print-certs</code>
 * of the Android SDK for the same files.</p>
 *
 * @author buttermdm
 */
public class ApkSigningBlockTests {

    private static String digest(String resource) throws Exception {
        File apk = new File(ApkSigningBlockTests.class.getResource(resource).toURI());
        byte[] certificate = ApkSigningBlock.getFirstCertificate(apk);
        Assert.assertNotNull("No certificate in " + resource, certificate);
        StringBuilder hex = new StringBuilder();
        for (byte b : MessageDigest.getInstance("SHA-256").digest(certificate)) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    public void testSignatureSchemeV2() throws Exception {
        Assert.assertEquals("04827cf3c4608192061aedc88f9edd13adff6f654aeb802a297deb9ffac0e632",
                digest("/apk/testpos-v2-signed.apk"));
    }

    @Test
    public void testSignatureSchemeV3() throws Exception {
        Assert.assertEquals("fdc41a716322a9a4252d9f61454c9c0debbbcd5a4ff5830e5b9a59dab45ab3a0",
                digest("/apk/testpos-v3-signed.apk"));
    }

    @Test
    public void testNotSigned() throws Exception {
        File zip = File.createTempFile("unsigned", ".zip");
        try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(zip))) {
            out.putNextEntry(new java.util.zip.ZipEntry("a.txt"));
            out.write("test".getBytes());
            out.closeEntry();
        }
        try {
            Assert.assertNull(ApkSigningBlock.getFirstCertificate(zip));
        } finally {
            zip.delete();
        }
    }
}
