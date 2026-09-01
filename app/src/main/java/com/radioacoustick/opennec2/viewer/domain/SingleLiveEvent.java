// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (c) 2026 Valery Kustarev (https://github.com/radioacoustick)
/*
 * This file is part of Open NEC2 Viewer.
 *
 * Open NEC2 Viewer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Open NEC2 Viewer is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Open NEC2 Viewer. If not, see <https://www.gnu.org/licenses/>.
 */

package com.radioacoustick.opennec2.viewer.domain;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * LiveData, which sends updates only ONCE after subscription.
 * Used for one-time events: Toast, Snackbar, Navigation.
 */
public class SingleLiveEvent<T> extends MutableLiveData<T> {

	private final AtomicBoolean mPending = new AtomicBoolean(false);

	@MainThread
	@Override
	public void observe(@NonNull LifecycleOwner owner, @NonNull final Observer<? super T> observer) {
		//if (hasActiveObservers()) {
			// Show warning if more than one observer is registered
		//}

		// Subscribing to parent LiveData
		super.observe(owner, t -> {
			if (mPending.compareAndSet(true, false)) {
				observer.onChanged(t);
			}
		});
	}

	@MainThread
	@Override
	public void setValue(@Nullable T t) {
		mPending.set(true);
		super.setValue(t);
	}

	/**
	 * Used to call an event without passing any specific data (similar to Void)
	 */
	@MainThread
	public void call() {
		setValue(null);
	}
}
