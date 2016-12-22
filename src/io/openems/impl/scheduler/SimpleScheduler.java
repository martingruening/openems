/*******************************************************************************
 * OpenEMS - Open Source Energy Management System
 * Copyright (c) 2016 FENECON GmbH and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * Contributors:
 *   FENECON GmbH - initial API and implementation and initial documentation
 *******************************************************************************/
package io.openems.impl.scheduler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.openems.api.bridge.Bridge;
import io.openems.api.channel.ConfigChannel;
import io.openems.api.channel.WriteChannel;
import io.openems.api.controller.Controller;
import io.openems.api.scheduler.Scheduler;
import io.openems.core.ThingRepository;

public class SimpleScheduler extends Scheduler {

	private ThingRepository thingRepository;

	public SimpleScheduler() {
		thingRepository = ThingRepository.getInstance();
	}

	private ConfigChannel<Integer> cycleTime = new ConfigChannel<Integer>("cycleTime", this, Integer.class)
			.defaultValue(500);

	@Override public ConfigChannel<Integer> cycleTime() {
		return cycleTime;
	}

	@Override protected void dispose() {}

	@Override protected void forever() {
		List<Controller> controllers = new ArrayList<>(this.controllers.values());
		Collections.sort(controllers, (c1, c2) -> c2.priority.valueOptional().orElse(Integer.MIN_VALUE)
				- c1.priority.valueOptional().orElse(Integer.MIN_VALUE));
		for (Controller controller : controllers) {
			// TODO: check if WritableChannels can still be changed, before executing
			controller.run();
		}
		for (WriteChannel<?> channel : thingRepository.getWriteChannels()) {
			channel.shadowCopyAndReset();
		}
		for (Bridge bridge : thingRepository.getBridges()) {
			bridge.triggerWrite();
		}
	}

	@Override protected boolean initialize() {
		return true;
	}
}
