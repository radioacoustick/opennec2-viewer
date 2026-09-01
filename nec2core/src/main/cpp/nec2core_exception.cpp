// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
/*
 * This file is part of nec2core engine for android app.
 *
 * Nec2core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Nec2core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Nec2core. If not, see <https://www.gnu.org/licenses/>.
 */

#include "nec2core_exception.h"

void throwJavaException(JNIEnv *env, const std::exception &e) {
    const char *javaClassName = "java/lang/RuntimeException";
    jclass java_ex_class = env->FindClass(javaClassName);
    if (java_ex_class) {
        env->ThrowNew(java_ex_class, e.what());
        env->DeleteLocalRef(java_ex_class);
    }
}

void throwJavaException(JNIEnv *env, const nec2core_exception &e) {
    const char *javaClassName = "java/lang/IllegalStateException";
    jclass java_ex_class = env->FindClass(javaClassName);
    if (java_ex_class) {
        env->ThrowNew(java_ex_class, e.get_message().c_str());
        env->DeleteLocalRef(java_ex_class);
    }
}

void throwJavaException(JNIEnv *env, const nec_exception &e) {
    const char *javaClassName = "java/lang/IllegalArgumentException";
    jclass java_ex_class = env->FindClass(javaClassName);
    if (java_ex_class) {
        env->ThrowNew(java_ex_class, e.get_message().c_str());
        env->DeleteLocalRef(java_ex_class);
    }
}
