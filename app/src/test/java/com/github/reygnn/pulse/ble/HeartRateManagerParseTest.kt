package com.github.reygnn.pulse.ble

import com.github.reygnn.pulse.ble.HeartRateManager.Companion.parseHeartRate
import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateManagerParseTest {

    // The parseHeartRate function is the BLE Heart Rate Profile decoder.
    // Flag bit 0 of byte[0] selects between UINT8 and UINT16-LE payloads.
    // See https://www.bluetooth.com/specifications/specs/heart-rate-service-1-0/

    @Test
    fun `empty payload returns zero, not a crash`() {
        assertEquals(0, parseHeartRate(byteArrayOf()))
    }

    @Test
    fun `flag bit 0 cleared - payload byte is the bpm UINT8`() {
        // flags = 0x00, hr = 60
        assertEquals(60, parseHeartRate(byteArrayOf(0x00, 60)))
    }

    @Test
    fun `flag bit 0 cleared - 0xFF reads as unsigned 255`() {
        // 0xFF.toInt() in Kotlin is -1 unless masked with 0xFF.
        // The implementation must mask, otherwise we'd get a negative.
        assertEquals(255, parseHeartRate(byteArrayOf(0x00, 0xFF.toByte())))
    }

    @Test
    fun `flag bit 0 cleared but only one byte returns zero`() {
        // Malformed payload: spec promises a measurement byte but it's missing.
        // The defensive bounds check prevents an ArrayIndexOutOfBoundsException.
        assertEquals(0, parseHeartRate(byteArrayOf(0x00)))
    }

    @Test
    fun `flag bit 0 set - payload is UINT16 little endian`() {
        // flags = 0x01, hr_lo = 0x2C (44), hr_hi = 0x01 → 0x012C = 300
        assertEquals(300, parseHeartRate(byteArrayOf(0x01, 0x2C, 0x01)))
    }

    @Test
    fun `flag bit 0 set - high byte zero degenerates to UINT8 value`() {
        // flags = 0x01, hr_lo = 75, hr_hi = 0x00 → 75
        assertEquals(75, parseHeartRate(byteArrayOf(0x01, 75, 0x00)))
    }

    @Test
    fun `flag bit 0 set - 0xFFFF reads as unsigned 65535`() {
        // Masking the high byte matters here too.
        assertEquals(
            65535,
            parseHeartRate(byteArrayOf(0x01, 0xFF.toByte(), 0xFF.toByte())),
        )
    }

    @Test
    fun `flag bit 0 set but only two bytes returns zero`() {
        // The payload claims a UINT16 but only one byte follows the flags.
        // Without the bounds check this would crash; the defensive return-0 lets
        // a bad sensor packet drop instead of taking the BLE callback down.
        assertEquals(0, parseHeartRate(byteArrayOf(0x01, 0x42)))
    }

    @Test
    fun `extra flag bits beyond bit 0 are ignored for the value parse`() {
        // Real HRMs set additional flag bits (sensor-contact, energy-expended,
        // RR-interval). Only bit 0 changes the format of the bpm field.
        // flags = 0x16 (RR + energy + sensor contact, but bit 0 = 0) → UINT8
        assertEquals(72, parseHeartRate(byteArrayOf(0x16, 72)))
        // flags = 0x17 → bit 0 = 1, UINT16
        assertEquals(72, parseHeartRate(byteArrayOf(0x17, 72, 0x00)))
    }
}
