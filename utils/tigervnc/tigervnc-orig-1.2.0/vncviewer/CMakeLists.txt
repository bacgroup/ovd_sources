include_directories(${FLTK_INCLUDE_DIR})
include_directories(${GETTEXT_INCLUDE_DIR})

include_directories(${CMAKE_SOURCE_DIR}/common)
set(VNCVIEWER_SOURCES
  buildTime.cxx
  menukey.cxx
  CConn.cxx
  DesktopWindow.cxx
  UserDialog.cxx
  ServerDialog.cxx
  OptionsDialog.cxx
  Viewport.cxx
  parameters.cxx
  keysym2ucs.c
  vncviewer.cxx)

if(WIN32)
  set(VNCVIEWER_SOURCES ${VNCVIEWER_SOURCES} win32.c)
endif()

if(APPLE)
  set(VNCVIEWER_SOURCES ${VNCVIEWER_SOURCES} cocoa.mm)
endif()

if(WIN32)
  set(VNCVIEWER_SOURCES ${VNCVIEWER_SOURCES} Win32PixelBuffer.cxx)
elseif(APPLE)
  set(VNCVIEWER_SOURCES ${VNCVIEWER_SOURCES} OSXPixelBuffer.cxx)
else()
  set(VNCVIEWER_SOURCES ${VNCVIEWER_SOURCES} X11PixelBuffer.cxx)
endif()

if(WIN32)
  add_executable(vncviewer WIN32 ${VNCVIEWER_SOURCES})
else()
  add_executable(vncviewer ${VNCVIEWER_SOURCES})
endif()

if(USE_INCLUDED_FLTK)
  target_link_libraries(vncviewer fltk_static)
endif()
target_link_libraries(vncviewer rfb network rdr os Xregion ${FLTK_LIBRARIES} ${GETTEXT_LIBRARIES})

# When building with GnuTLS, librdr depends on ws2_32, so in order to make
# MinGW happy, we need to put ws2_32 in librdr's target_link_libraries string,
# not here.
if(NOT GNUTLS_FOUND AND WIN32)
  target_link_libraries(vncviewer ws2_32)
endif()

if(WIN32)
  install(TARGETS vncviewer DESTINATION .)
else()
  install(TARGETS vncviewer DESTINATION bin)
endif()
if(UNIX)
  install(FILES vncviewer.man DESTINATION man/man1 RENAME vncviewer.1)
endif()
