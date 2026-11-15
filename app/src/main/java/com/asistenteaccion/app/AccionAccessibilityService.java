package com.asistenteaccion.app;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

public class AccionAccessibilityService extends AccessibilityService {
    private static AccionAccessibilityService instance;
    public static AccionAccessibilityService getInstance() { return instance; }
    @Override public void onServiceConnected() { instance = this; super.onServiceConnected(); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event) { }
    @Override public void onInterrupt() { }
    @Override public void onDestroy() { if (instance == this) instance = null; super.onDestroy(); }
    public boolean volver() { return performGlobalAction(GLOBAL_ACTION_BACK); }
    public boolean inicio() { return performGlobalAction(GLOBAL_ACTION_HOME); }
    public boolean escribir(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        AccessibilityNodeInfo target = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
        if (target == null) target = findEditable(root);
        if (target == null) return false;
        Bundle args = new Bundle(); args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        return target.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args);
    }
    private AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        if (node.isEditable()) return node;
        for (int i=0; i<node.getChildCount(); i++) { AccessibilityNodeInfo found = findEditable(node.getChild(i)); if (found != null) return found; }
        return null;
    }
}
