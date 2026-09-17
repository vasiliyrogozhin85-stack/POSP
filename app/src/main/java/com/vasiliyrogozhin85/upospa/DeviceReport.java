package com.vasiliyrogozhin85.upospa;

import android.content.Context;
import java.io.File;

final class DeviceReport {
    static File collect(Context ctx) throws Exception {
        return DeviceSnapshot.collectToFile(ctx);
    }
    private DeviceReport() {}
}
