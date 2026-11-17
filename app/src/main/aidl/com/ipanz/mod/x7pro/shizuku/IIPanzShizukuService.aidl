package com.ipanz.mod.x7pro.shizuku;

interface IIPanzShizukuService {
    void destroy() = 16777114;
    boolean trimCaches(long targetBytes) = 1;
    boolean forceStopApp(String packageName) = 2;
}
