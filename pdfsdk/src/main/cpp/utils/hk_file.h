#ifndef PDFVIEW_HK_FILE_H
#define PDFVIEW_HK_FILE_H

#include <stdlib.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct __file_struct file_struct;
typedef struct __file_struct *file_t;

#define FS_INVALID_MODE     65535
#define FS_NOT_VALID        -10
#define FS_FILE             -9
#define FS_DIRECTORY        -8
#define FS_SYMLINK          -7   /* currently not used */

#define FS_NOT_EMPTY        -6

#define FS_EXISTS            0
#define FS_NO_EXISTS        -2

#define FS_SUCCESS           0
#define FS_FAILURE          -1

/*******************************************************************************
*   Utility Functions
*******************************************************************************/

/*  Identify the type of object pointed to by path
    Returns:
        FS_DIRECTORY    - Path is a directory
        FS_FILE         - Path is a file
        FS_NO_EXISTS    - Path does not point to anything
        FS_NOT_VALID    - invalid path name
*/
int fs_identify_path(const char* path);

/*  Determines is a path is a symlink
    Returns:
        FS_SUCCESS      - If the path is a symlink
        FS_FAILURE      - If the path is not a symlink
*/
int fs_is_symlink(const char* path);

/*  Returns the current working directory
    NOTE: Up to the user to free the resulting memory */
char* fs_cwd();

/*  Resolve the path provided by turning it into an absolute path;
    does not keep filename!
    NOTE: Up to the caller to free the resulting memory
    NOTE: This does not validate the resulting path! */
char* fs_resolve_path(const char* path);

/*  Build and return the canonical absolute path to the file using the path
    and filename provided
    NOTE: Up to the caller to free the resulting memory
    NOTE: Up to the caller to pass in an array large enough for the results "res"
*/
char* fs_combine_filepath(const char* path, const char* filename);
char* fs_combine_filepath_alt(const char* path, const char* filename, char* res);

/*  Rename or move a file from current path to the new path
    Returns:
        FS_SUCCESS
        FS_FAILURE
        FS_NOT_VALID    - invalid path name
*/
int fs_rename(const char* path, const char* new_path);
int fs_move(const char* path, const char* new_path);

/*  Touch a file at path by creating it if it doesn't already exist
    Returns:
        FS_SUCCESS
        FS_FAILURE
        FS_NOT_VALID    - invalid path name
*/
int fs_touch(const char* path);
int fs_touch_alt(const char* path, mode_t mode);

/*  Remove the file to which path points
    Returns:
        FS_SUCCESS
        FS_FAILURE
        FS_NOT_VALID    - invalid path name or not a file
*/
int fs_remove_file(const char* path);

/*  Make the directory structure pointed to by path; recursively if desired
    Returns:
        FS_EXISTS       - Path already exists
        FS_SUCCESS      - Path successfully created
        FS_FAILURE      - Unable to create a directory
        FS_NOT_VALID    - invalid path name
*/
int fs_mkdir(const char* path, bool recursive);
int fs_mkdir_alt(const char* path, bool recursive, mode_t mode);

/*  Return the raw mode including filetype information
    Returns:
        FS_NOT_VALID
        FS_NO_EXISTS
        FS_FAILURE
        mode_t as int
*/
int fs_get_raw_mode(const char* path);

/*  Retrieve the permissions of the file or directory at which path points
    Returns:
        FS_NOT_VALID
        FS_NO_EXISTS
        FS_FAILURE
        mode_t as int
*/
int fs_get_permissions(const char* path);

/*  Set the permission of the file or directory
    Returns:
        FS_NOT_VALID
        FS_SUCCESS
        FS_FAILURE
*/
int fs_set_permissions(const char* path, mode_t mode);

/*  Turn the permissions mode_t (int) into a printable format "drwxrwxrwx"
    NOTE: Up to the caller to free the returned memory */
char* fs_mode_to_string(mode_t mode);

/*  Turn the permissions mode_t (int) into a printable format "drwxrwxrwx"
    NOTE: If res is NULL then memory will be allocated and the caller will
          need to free the memory
    NOTE: If res is memory already allocated to hold the string, it must be at
          least 11 characters in length */
char* fs_mode_to_string_alt(mode_t mode, char* res);

/*  Turn a permissions string "drwxrwxrwx" into the mode flag (int) or
    FS_INVALID_MODE if the provided string does not contain enough
    information or is invalid. */
unsigned short fs_string_to_mode(const char* s);

/*  Remove the directory pointed to by path; to remove all sub-directories and
    files, use the `fs_rmdir_alt` function and set recursive to `true`.
    Returns:
        FS_NOT_VALID    - bad input
        FS_NO_EXISTS    - directory already does not exist
        FS_FAILURE      - error, likely read/write access or change during operation
        FS_SUCCESS      - on success signifies that the directory no longer exists
*/
int fs_rmdir(const char* path);
int fs_rmdir_alt(const char* path, bool recursive);

/*  List all the files and directories of the provided path. Items is a passed
    by reference int that, on completion, will identify how many records are
    returned. If error, items will be set to 0
    Returns:
        NULL if error or if path is not a directory
*/
char** fs_list_dir(const char* path, int* items);

ssize_t fs_get_size_for_fd(const int fd);

/**
 *  Initialize the file_t object and pull information about the file pointed to
    by filepath
 * @param filepath If filepath doesn't point to a file or symlink
 * @return Allocated memory with all members set except for reading the
           file into memory
 */
file_t f_init(const char* filepath);
file_t f_init_by_fd(int fd);

/*  Free the memory held by the file_t object */
void f_free(file_t f);

/*  Returns the size of the file, in bytes */
size_t f_filesize(file_t f);

ssize_t f_pread(file_t f, void *buf, size_t count, off_t offset);

#ifdef __cplusplus
}
#endif


#endif //PDFVIEW_HK_FILE_H
