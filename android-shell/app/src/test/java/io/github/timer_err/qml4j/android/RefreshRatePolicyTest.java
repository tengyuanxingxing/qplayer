package io.github.timer_err.qml4j.android;

import org.junit.Test;
import static org.junit.Assert.*;
public class RefreshRatePolicyTest {
    @Test public void choosesHighestAvailableAtCurrentResolutionWithout120Cap() {
        assertEquals(165f,RefreshRatePolicy.choose(1216,2640,60f,new int[]{1216,1216,1216,1216},
                new int[]{2640,2640,2640,2640},new float[]{60,90,120,165},new float[0],false),0.01f);
    }
    @Test public void neverChangesResolutionToChaseRefreshRate() {
        assertEquals(120f,RefreshRatePolicy.choose(1080,2400,60f,new int[]{1080,720},new int[]{2400,1600},
                new float[]{120,240},new float[0],false),0.01f);
    }
    @Test public void includesSeamlessAlternativesAndKeepsFractionalRates() {
        assertEquals(143.98f,RefreshRatePolicy.choose(1080,2400,59.94f,new int[]{1080},new int[]{2400},
                new float[]{59.94f},new float[]{119.88f,143.98f,Float.NaN},false),0.001f);
    }
    @Test public void honorsPowerSaveAndLowRefreshDevices() {
        assertEquals(0f,RefreshRatePolicy.choose(1080,2400,120f,new int[0],new int[0],new float[0],new float[]{165},true),0f);
        assertEquals(60f,RefreshRatePolicy.choose(1080,2400,60f,new int[0],new int[0],new float[0],new float[0],false),0f);
    }
}
