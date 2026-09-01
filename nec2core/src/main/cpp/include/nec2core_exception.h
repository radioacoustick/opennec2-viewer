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

#ifndef NEC2CORE_EXCEPTION_H
#define NEC2CORE_EXCEPTION_H

#include <jni.h>
#include "nec_exception.h"
#include "exception"
#include "string"

/**
 * Handling NEC2CORE module exceptions
 */
class nec2core_exception : public std::exception {
    std::string msg;
public:
    explicit nec2core_exception(const std::string& m) : msg(m) {}
    const char* what() const noexcept override { return msg.c_str(); }
    const std::string& get_message() const noexcept { return msg; }
};

// Java's Generic Exception Throwing Function
void throwJavaException(JNIEnv* env, const std::exception& e);
void throwJavaException(JNIEnv* env, const nec2core_exception& e);
void throwJavaException(JNIEnv* env, const nec_exception& e);

#endif