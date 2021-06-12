include_directories(./)

add_library( # Sets the name of the library.
        hk_utils

        # Sets the library as a shared library.
        STATIC

        # Provides a relative path to your source file(s).
        hk_file.cpp )