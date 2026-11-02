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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * <p>Reads the first signing certificate of an APK from its APK Signing Block (signature scheme v3 or v2), as
 * described in the Android documentation of the APK signature schemes. The certificate is not verified: it only
 * identifies the signing key.</p>
 *
 * @author buttermdm
 */
final class ApkSigningBlock {

    private static final long MAGIC_LO = 0x20676953204b5041L;   // "APK Sig "
    private static final long MAGIC_HI = 0x3234206b636f6c42L;   // "Block 42"
    private static final int V2_ID = 0x7109871a;
    private static final int V3_ID = 0xf05368c0;
    private static final int EOCD_MIN_SIZE = 22;
    private static final int MAX_COMMENT_SIZE = 65535;
    private static final int MAX_BLOCK_SIZE = 64 * 1024 * 1024;

    private ApkSigningBlock() {
    }

    /**
     * <p>Gets the DER-encoded first certificate of the first signer.</p>
     *
     * @param apk an APK file.
     * @return the certificate or <code>null</code> if the APK has no v2/v3 signature.
     * @throws IOException if the file can't be read or is malformed.
     */
    static byte[] getFirstCertificate(File apk) throws IOException {
        try (RandomAccessFile file = new RandomAccessFile(apk, "r")) {
            long centralDirectoryOffset = findCentralDirectoryOffset(file);
            if (centralDirectoryOffset < 24) {
                return null;
            }
            ByteBuffer footer = read(file, centralDirectoryOffset - 24, 24);
            long blockSizeInFooter = footer.getLong();
            if (footer.getLong() != MAGIC_LO || footer.getLong() != MAGIC_HI) {
                return null;
            }
            if (blockSizeInFooter < 24 || blockSizeInFooter > MAX_BLOCK_SIZE) {
                throw new IOException("Invalid APK Signing Block size");
            }
            long blockStart = centralDirectoryOffset - blockSizeInFooter - 8;
            if (blockStart < 0) {
                throw new IOException("Invalid APK Signing Block offset");
            }
            // The pairs follow the leading size field and end before the footer (size + magic)
            ByteBuffer pairs = read(file, blockStart + 8, (int) (blockSizeInFooter - 24));
            ByteBuffer v2 = null;
            ByteBuffer v3 = null;
            while (pairs.remaining() >= 12) {
                long length = pairs.getLong();
                if (length < 4 || length > pairs.remaining()) {
                    throw new IOException("Invalid APK Signing Block pair");
                }
                int id = pairs.getInt();
                ByteBuffer value = slice(pairs, (int) length - 4);
                if (id == V3_ID) {
                    v3 = value;
                } else if (id == V2_ID) {
                    v2 = value;
                }
            }
            ByteBuffer scheme = v3 != null ? v3 : v2;
            return scheme != null ? firstCertificate(scheme) : null;
        }
    }

    /**
     * <p>The v2 and v3 blocks have the same outer structure: signers / signer / signed data / certificates.</p>
     */
    private static byte[] firstCertificate(ByteBuffer scheme) throws IOException {
        ByteBuffer signers = lengthPrefixed(scheme);
        ByteBuffer signer = lengthPrefixed(signers);
        ByteBuffer signedData = lengthPrefixed(signer);
        lengthPrefixed(signedData);                                 // digests
        ByteBuffer certificates = lengthPrefixed(signedData);
        ByteBuffer certificate = lengthPrefixed(certificates);
        byte[] result = new byte[certificate.remaining()];
        certificate.get(result);
        return result;
    }

    private static long findCentralDirectoryOffset(RandomAccessFile file) throws IOException {
        long fileSize = file.length();
        int tailSize = (int) Math.min(fileSize, EOCD_MIN_SIZE + MAX_COMMENT_SIZE);
        ByteBuffer tail = read(file, fileSize - tailSize, tailSize);
        for (int i = tailSize - EOCD_MIN_SIZE; i >= 0; i--) {
            if (tail.getInt(i) == 0x06054b50) {
                return tail.getInt(i + 16) & 0xffffffffL;
            }
        }
        throw new IOException("Not a ZIP file");
    }

    private static ByteBuffer read(RandomAccessFile file, long offset, int size) throws IOException {
        byte[] bytes = new byte[size];
        file.seek(offset);
        file.readFully(bytes);
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static ByteBuffer lengthPrefixed(ByteBuffer buffer) throws IOException {
        if (buffer.remaining() < 4) {
            throw new IOException("Truncated signature data");
        }
        int length = buffer.getInt();
        if (length < 0 || length > buffer.remaining()) {
            throw new IOException("Invalid length in signature data");
        }
        return slice(buffer, length);
    }

    private static ByteBuffer slice(ByteBuffer buffer, int length) {
        ByteBuffer result = buffer.slice().order(ByteOrder.LITTLE_ENDIAN);
        result.limit(length);
        buffer.position(buffer.position() + length);
        return result;
    }
}
