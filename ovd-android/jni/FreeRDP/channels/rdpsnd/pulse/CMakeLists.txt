# FreeRDP: A Remote Desktop Protocol Client
# FreeRDP cmake build script
#
# Copyright 2011 O.S. Systems Software Ltda.
# Copyright 2011 Otavio Salvador <otavio@ossystems.com.br>
# Copyright 2011 Marc-Andre Moreau <marcandre.moreau@gmail.com>
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

set(RDPSND_PULSE_SRCS
	rdpsnd_pulse.c
)

include_directories(..)
include_directories(${PULSE_INCLUDE_DIRS})

add_library(rdpsnd_pulse ${RDPSND_PULSE_SRCS})
set_target_properties(rdpsnd_pulse PROPERTIES PREFIX "")

target_link_libraries(rdpsnd_pulse freerdp-utils)
target_link_libraries(rdpsnd_pulse ${PULSEAUDIO_LIBRARY})

install(TARGETS rdpsnd_pulse DESTINATION ${FREERDP_PLUGIN_PATH})
