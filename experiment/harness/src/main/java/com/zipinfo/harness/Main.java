package com.zipinfo.harness;

import com.zipinfo.ai.AiCore;

/** Entry point of the experiment harness. Build order 1 keeps only this skeleton. */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.out.println(describe());
    }

    static String describe() {
        return "harness skeleton with " + AiCore.class.getName();
    }
}
