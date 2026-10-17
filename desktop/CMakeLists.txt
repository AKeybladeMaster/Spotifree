cmake_minimum_required(VERSION 3.21)
project(Spotifree VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTORCC ON)
set(CMAKE_EXPORT_COMPILE_COMMANDS ON)

find_package(Qt6 6.8 REQUIRED COMPONENTS Core Widgets WebEngineCore WebEngineWidgets Test)
qt_standard_project_setup(REQUIRES 6.8)

qt_add_library(spotifree_policy STATIC
    src/UrlPolicy.cpp
    src/UrlPolicy.h
    src/HostFilter.cpp
    src/HostFilter.h
)
target_include_directories(spotifree_policy PUBLIC src)
target_link_libraries(spotifree_policy PUBLIC Qt6::Core Qt6::WebEngineCore)
target_compile_features(spotifree_policy PUBLIC cxx_std_17)

qt_add_executable(spotifree MANUAL_FINALIZATION
    src/main.cpp
    src/AppWindow.cpp
    src/AppWindow.h
    src/BrowserPage.cpp
    src/BrowserPage.h
    assets.qrc
)
set_target_properties(spotifree PROPERTIES OUTPUT_NAME "Spotifree")
if(APPLE)
    set_target_properties(spotifree PROPERTIES MACOSX_BUNDLE TRUE
        MACOSX_BUNDLE_GUI_IDENTIFIER "com.spotifree.player"
        MACOSX_BUNDLE_BUNDLE_NAME "Spotifree")
elseif(WIN32)
    set_target_properties(spotifree PROPERTIES WIN32_EXECUTABLE TRUE)
endif()
target_link_libraries(spotifree PRIVATE spotifree_policy Qt6::Widgets Qt6::WebEngineWidgets)
qt_finalize_executable(spotifree)

if(MSVC)
    target_compile_options(spotifree PRIVATE /W4)
else()
    target_compile_options(spotifree PRIVATE -Wall -Wextra -Wpedantic)
endif()

enable_testing()
qt_add_executable(spotifree_policy_test tests/UrlPolicyTest.cpp)
target_link_libraries(spotifree_policy_test PRIVATE spotifree_policy Qt6::Test)
add_test(NAME policy COMMAND spotifree_policy_test)

install(TARGETS spotifree
    BUNDLE DESTINATION .
    RUNTIME DESTINATION bin)

qt_generate_deploy_app_script(
    TARGET spotifree
    OUTPUT_SCRIPT deploy_script
    NO_UNSUPPORTED_PLATFORM_ERROR
)
install(SCRIPT ${deploy_script})

if(UNIX AND NOT APPLE)
    install(FILES assets/spotifree.desktop DESTINATION share/applications)
    install(FILES assets/spotifree.png DESTINATION share/icons/hicolor/256x256/apps)
endif()
