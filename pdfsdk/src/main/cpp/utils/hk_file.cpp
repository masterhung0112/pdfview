#include "hk_file.h"

#include <string.h>         /* strlen, strcmp, strchr, strncpy, strpbrk */
#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>         /* getcwd */
#include <sys/stat.h>
#include <fcntl.h>          /* O_CREAT */
#include <dirent.h>         /* */
#include <errno.h>

//typedef int mode_t;

typedef struct __file_struct {
    int fd;
    size_t filesize;
    mode_t mode;
    size_t num_lines;
    bool is_symlink;     /* 0 for false, 1 for true */
    char* basepath;
    char* filename;
    char* extension;
    char* absolute_path;
    char* buffer;       /* this will hold the whole file and lines will index into it */
    char** lines;
} __file_struct;

int fs_identify_path(const char* path) {
    if (path == NULL)
        return FS_NOT_VALID;

    errno = 0;
    struct stat stats;
    if (stat(path, &stats) == -1) {
        if (errno == ENOENT)
            return FS_NO_EXISTS;
    }
    if (S_ISDIR(stats.st_mode) != 0)
        return FS_DIRECTORY;
    if (S_ISREG(stats.st_mode) != 0)
        return FS_FILE;
    return FS_NOT_VALID;
}

/* private functions */
static char*   __str_duplicate(const char* s);
static char*   __str_extract_substring(const char* s, size_t start, size_t length);
static int     __str_find_reverse(const char* s, const char c);
static char**  __str_split_string_any(char* s, const char* s2, size_t* num);
static int     __str_find_any(const char* s, const char* s2);
static size_t  __str_find_cnt_any(const char* s, const char* s2);
static void    __parse_file_info(const char* full_filepath, char** filepath, char** filename);
static void    __free_double_array(char** arr, size_t num_elms);
static int     __cmp_str(const void* a, const void* b);
/* wrapper functions for windows and posix systems support */
static int     __fs_mkdir(const char* path, mode_t mode);
static int     __fs_rmdir(const char* path);
static char**  __fs_list_dir(const char* path, int* elms);
static int is_symlink(struct stat *stats);
static file_t init_with_stat(struct stat *stats, const char *filepath);

int fs_is_symlink(const char* path) {
    if (path == NULL)
        return FS_FAILURE;

    errno = 0;
    struct stat stats;
    if (lstat(path, &stats) == -1) {
        if (errno == ENOENT)
            return FS_FAILURE;
    }
    if (S_ISLNK(stats.st_mode) != 0)
        return FS_SUCCESS;
    return FS_FAILURE;
}

char* fs_resolve_path(const char* path) {
    if (path == NULL)
        return NULL;
    else if (path[0] == '.' && strlen(path) == 1)
        return fs_cwd();

    char* new_path = NULL;
    char* tmp = __str_duplicate(path);
    int pos = __str_find_reverse(tmp, '/');

    if (pos == -1) {
        char* cwd = fs_cwd();
        new_path = (char*)calloc(strlen(cwd) + 2 + strlen(path), sizeof(char));
        snprintf(new_path, strlen(cwd) + 2 + strlen(path), "%s/%s", cwd, path);
        free(cwd);
    }

    while (pos != -1) {
        tmp[pos] = '\0';
        char* p = realpath(tmp, NULL);
        if (p != NULL) {
            char* s = tmp + (pos + 1);
            int p_len = strlen(p), t_len = strlen(s);
            new_path = (char*)calloc(p_len + t_len + 3, sizeof(char));  /* include slash x2 and \0 */
            snprintf(new_path, p_len + 2 + t_len, "%s/%s", p, s);
            free(p);
            break;
        }
        int tmp_pos = __str_find_reverse(tmp, '/');
        pos[tmp] = '/';
        pos = tmp_pos;
    }
    free(tmp);

    /* ensure no trailing '/' */
    int len = strlen(new_path);
    if (new_path[len - 1] == '/')
        new_path[len - 1] = '\0';

    return new_path;
}

char* fs_combine_filepath(const char* path, const char* filename) {
    return fs_combine_filepath_alt(path, filename, NULL);
}

char* fs_combine_filepath_alt(const char* path, const char* filename, char* res) {
    if (path == NULL && filename == NULL)
        return NULL;  /* error case */
    else if (path == NULL && filename != NULL) {
        if (res == NULL)
            return __str_duplicate(filename);
        return strcpy(res, filename);
    }
    else if (path != NULL && filename == NULL) {
        if (res == NULL)
            return __str_duplicate(path);
        return strcpy(res, path);
    }

    int p_len = strlen(path);
    int f_len = strlen(filename);

    if (res == NULL)
        res = (char*)calloc(p_len + f_len + 2, sizeof(char)); /* 2 for / and NULL */

    strcpy(res, path);
    if (res[p_len - 1] == '/') {
        --p_len;
    }
    res[p_len] = '/';
    strcpy(res + 1 + p_len, filename);

    return res;
}

char* fs_cwd() {
    size_t malsize = 16; /* some defult power of 2... */
    char* buf = (char*)malloc(malsize);
    errno = 0;
    while(getcwd(buf, malsize) == NULL && errno == ERANGE) {
        malsize *= 2;
        char* tmp = (char*)realloc(buf, malsize * sizeof(char));
        buf = tmp;
    }
    return buf;
}

int fs_rename(const char* path, const char* new_path) {
    if (path == NULL || new_path == NULL)
        return FS_NOT_VALID;

    errno = 0;
    int res = rename(path, new_path);
    if (res == 0)
        return FS_SUCCESS;
    if (errno == ENOENT)
        return FS_NO_EXISTS;
    return FS_FAILURE;
}

int fs_move(const char* path, const char* new_path) {
    return fs_rename(path, new_path);
}

int fs_touch(const char* path) {
    return fs_touch_alt(path, S_IRWXU | S_IRGRP | S_IWGRP);
}

int fs_touch_alt(const char* path, mode_t mode) {
    if (path == NULL)
        return FS_NOT_VALID;

    int pfd;
    if ((pfd = open(path, O_CREAT, mode)) == -1) {
        close(pfd);
        return FS_FAILURE;
    }
    close(pfd);

    if (fs_identify_path(path) == FS_FILE) {
        fs_set_permissions(path, mode);
        return FS_SUCCESS;
    }
    return FS_FAILURE;
}

int fs_remove_file(const char* path) {
    int res = fs_identify_path(path);
    if (res != FS_FILE)
        return FS_NOT_VALID;

    res = remove(path);  /* should supports windows and posix OSes */
    if (res == 0)
        return FS_SUCCESS;
    return FS_FAILURE;
}

int fs_mkdir(const char* path, bool recursive) {
    return fs_mkdir_alt(path, recursive, S_IRWXU | S_IRGRP | S_IWGRP | S_IROTH);  /* drwxrw-r-- */
}

int fs_mkdir_alt(const char* path, bool recursive, mode_t mode) {
    if (path == NULL)
        return FS_NOT_VALID;
    size_t len = strlen(path);
    if (len == 0)
        return FS_NOT_VALID;

    errno = 0;
    struct stat stats;
    if (stat(path, &stats) != -1) {
        return FS_EXISTS;
    }

    if (!recursive) {
        return __fs_mkdir(path, mode);
    }

    /* need to start by finding a way to resolve the relative paths! */
    char* new_path = fs_resolve_path(path);
    if (new_path == NULL)
        return FS_NOT_VALID;

    /* add a trailing '/' for the loop to work! */
    len = strlen(new_path);
    char* tmp = (char*)realloc(new_path, len + 2);
    tmp[len] = '/';
    tmp[len + 1] = '\0';
    new_path = tmp;
    tmp = NULL;

    char* p;
    for (p = strchr(new_path + 1, '/'); p != NULL; p = strchr(p + 1, '/')) {
        *p = '\0';
        int res = __fs_mkdir(new_path, mode);
        if (res == FS_FAILURE) {
            free(new_path);
            return FS_FAILURE;
        }
        *p = '/';
    }
    free(new_path);
    return FS_SUCCESS;
}

int fs_rmdir(const char* path) {
    return fs_rmdir_alt(path, false);  /* do not default to recursive! */
}

int fs_rmdir_alt(const char* path, bool recursive) {
    int res = fs_identify_path(path);
    if (res == FS_NO_EXISTS)
        return FS_NO_EXISTS;
    if (res != FS_DIRECTORY)
        return FS_NOT_VALID;

    if (recursive == false)
        return __fs_rmdir(path);

    /* recursively go through and clean everything up... */
    if (__fs_rmdir(path) == FS_NOT_EMPTY) {
        int num_elms = 0;
        char** paths = __fs_list_dir(path, &num_elms);

        int i;
        for (i = 0; i < num_elms; ++i) {
            char* tmp = fs_combine_filepath(path, paths[i]);

            int type = fs_identify_path(tmp);
            if (type == FS_FILE) {
                fs_remove_file(tmp);
            } else if (type == FS_DIRECTORY) {
                int val = fs_rmdir_alt(tmp, recursive);
                if (val == FS_FAILURE) {
                    /* free the paths! */
                    __free_double_array(paths, num_elms);
                    free(tmp);
                    return FS_FAILURE;
                }
            } else {
                /* free the paths! */
                __free_double_array(paths, num_elms);
                free(tmp);
                return FS_FAILURE;  /* something went wrong; a symlink or something else was encountered... */
            }
            free(tmp);
        }

        /* free the paths! */
        __free_double_array(paths, num_elms);
        fs_rmdir(path);
    }
    return FS_SUCCESS;
}

char** fs_list_dir(const char* path, int* items) {
    *items = 0; /* set the easy default */
    if (fs_identify_path(path) != FS_DIRECTORY)
        return NULL;
    return __fs_list_dir(path, items);
}

int fs_get_raw_mode(const char* path) {
    if (path == NULL)
        return FS_NOT_VALID;
    struct stat stats;
    if (stat(path, &stats) == -1) {
        if (errno == ENOENT)
            return FS_NO_EXISTS;
        return FS_FAILURE;
    }
    return stats.st_mode;
}

int fs_get_permissions(const char* path) {
    int t_mode = fs_get_raw_mode(path);
    if (t_mode == FS_NOT_VALID || t_mode == FS_FAILURE || t_mode == FS_NO_EXISTS)
        return t_mode;
    return t_mode & (S_IRWXU | S_IRWXG | S_IRWXO);
}

int fs_set_permissions(const char* path, mode_t mode) {
    int res = fs_identify_path(path);
    if (res != FS_FILE && res != FS_DIRECTORY)
        return FS_NOT_VALID;

    res = chmod(path, mode);
    if (res == 0)
        return FS_SUCCESS;
    return FS_FAILURE;
}

char* fs_mode_to_string(mode_t mode) {
    return fs_mode_to_string_alt(mode, NULL);
}

char* fs_mode_to_string_alt(mode_t mode, char* res) {
    char* tmp;
    if (res == NULL)
        tmp = (char*)calloc(11, sizeof(char));
    else
        tmp = res;

    tmp[0] = S_ISDIR(mode) == 0 ? '-' : 'd';
    tmp[1] = (mode & S_IRUSR) ? 'r' : '-';
    tmp[2] = (mode & S_IWUSR) ? 'w' : '-';
    tmp[3] = (mode & S_IXUSR) ? 'x' : '-';
    tmp[4] = (mode & S_IRGRP) ? 'r' : '-';
    tmp[5] = (mode & S_IWGRP) ? 'w' : '-';
    tmp[6] = (mode & S_IXGRP) ? 'x' : '-';
    tmp[7] = (mode & S_IROTH) ? 'r' : '-';
    tmp[8] = (mode & S_IWOTH) ? 'w' : '-';
    tmp[9] = (mode & S_IXOTH) ? 'x' : '-';
    tmp[10] = '\0';
    res = tmp;
    tmp = NULL;
    return res;
}

unsigned short fs_string_to_mode(const char* s) {
    unsigned short res = 0;
    if (s == NULL || strlen(s) != 10)
        return FS_INVALID_MODE;
    /* skip the directory char */
    res |= s[1] == 'r' ? S_IRUSR : 0;
    res |= s[2] == 'w' ? S_IWUSR : 0;
    res |= s[3] == 'x' ? S_IXUSR : 0;
    res |= s[4] == 'r' ? S_IRGRP : 0;
    res |= s[5] == 'w' ? S_IWGRP : 0;
    res |= s[6] == 'x' ? S_IXGRP : 0;
    res |= s[7] == 'r' ? S_IROTH : 0;
    res |= s[8] == 'w' ? S_IWOTH : 0;
    res |= s[9] == 'x' ? S_IXOTH : 0;
    return res;
}

ssize_t fs_get_size_for_fd(const int fd) {
    struct stat stats;
    if (fstat(fd, &stats) == -1) {
        /*__print_out_stat_errno(errno); */
        return -1;
    }

    return stats.st_size;
}

/*******************************************************************************
*   file Objects
*******************************************************************************/

file_t init_with_stat(struct stat *stats, const char *filepath) {
    mode_t mode = stats->st_mode;
    if (S_ISREG(mode) == 0)
        return NULL; /* it isn't a file or symlink */

    file_t f = (file_t)calloc(1, sizeof(file_struct));
    /* set the defaults */
    f->filename = NULL;
    f->extension = NULL;
    f->absolute_path = NULL;
    f->filesize = 0;
    f->num_lines = 0;
    f->lines = NULL;
    f->mode = mode;
    f->filesize = stats->st_size;
    f->is_symlink = is_symlink(reinterpret_cast<struct stat *>(stat)) == FS_SUCCESS ? true : false;

    if (filepath != NULL) {
        char *path = NULL;
        __parse_file_info(filepath, &path, &f->filename);
        f->basepath = realpath(path, NULL);
        f->absolute_path = fs_combine_filepath(f->basepath, f->filename);
        free(path);
        int ex_pos = __str_find_reverse(f->filename, '.');
        if (ex_pos != -1)
            f->extension = __str_extract_substring(f->filename, ex_pos + 1, strlen(f->filename));
    }
    return f;
}

file_t f_init(const char* filepath) {
    errno = 0;
    struct stat stats;
    if (stat(filepath, &stats) == -1) {
        /*__print_out_stat_errno(errno); */
        return NULL;
    }

    return init_with_stat((struct stat *)&stat, filepath);
}

file_t f_init_by_fd(int fd) {
    errno = 0;
    struct stat stats;
    if (fstat(fd, &stats) == -1) {
        /*__print_out_stat_errno(errno); */
        return NULL;
    }

    return init_with_stat((struct stat *)&stat, nullptr);
}

void f_free(file_t f) {
    free(f->basepath);
    free(f->filename);
    free(f->extension);
    free(f->absolute_path);
    size_t i;
    for (i = 0; i < f->num_lines; ++i)
        f->lines[i] = NULL;
    free(f->lines);
    free(f->buffer);

    /* Set everything to a default value */
    f->filesize = 0;
    f->mode = 0;
    f->num_lines = 0;
    f->is_symlink = false;
    f->basepath = NULL;
    f->filename = NULL;
    f->extension = NULL;
    f->absolute_path = NULL;
    f->buffer = NULL;
    f->lines = NULL;

    free(f);
}

size_t f_filesize(file_t f) {
    return f->filesize;
}

ssize_t f_pread(file_t f, void *buf, size_t count, off_t offset) {

}
/*******************************************************************************
*   PRIVATE FUNCTIONS
*******************************************************************************/
static int __fs_mkdir(const char* path, mode_t mode) {
    errno = 0;
    int res = mkdir(path, mode);
    if (res == -1) {
        if (errno != EEXIST) {
            return FS_FAILURE;
        }
    }
    return FS_EXISTS;
}

static int __fs_rmdir(const char* path) {
    errno = 0;
    int res = rmdir(path);
    if (res == -1) {
        if (errno == EEXIST || errno == ENOTEMPTY)
            return FS_NOT_EMPTY;
        return FS_FAILURE;
    }
    return FS_SUCCESS;
}

static char** __fs_list_dir(const char* path, int* elms) {
    int growth_num = 10;
    int cur_size = growth_num;
    char** paths = (char**)calloc(cur_size, sizeof(char*));

    DIR *d;
    d = opendir(path);
    int el_num = 0;
    if (d) {
        struct dirent *dir;
        while ((dir = readdir(d)) != NULL) {
            /* need to skip "." and ".." */
            int item_len = strlen(dir->d_name);
            if (item_len == 1 && dir->d_name[0] == '.')
                continue;
            else if (item_len == 2 && strcmp(dir->d_name, "..") == 0)
                continue;
            paths[el_num++] = __str_duplicate(dir->d_name);

            if (el_num == cur_size) {
                cur_size += growth_num;
                char** tmp = (char**)realloc(paths, sizeof(char*) * cur_size);
                paths = tmp;
            }
        }
        closedir(d);
    }

    if (cur_size != el_num) {
        char** tmp = (char**)realloc(paths, sizeof(char*) * el_num);
        paths = tmp;
    }
    *elms = el_num;
    qsort(paths, el_num, sizeof(const char*), __cmp_str);
    return paths;
}

static char* __str_duplicate(const char* s) {
    size_t len = strlen(s);
    char* buf = (char*)malloc((len + 1) * sizeof(char));
    if (buf == NULL)
        return NULL;
    strcpy(buf, s);
    buf[len] = '\0';
    return buf;
}

static int __str_find_reverse(const char* s, const char c) {
    char* loc = strrchr((char*)s, c);
    if (loc == NULL)
        return -1;
    return loc - s;
}

static char* __str_extract_substring(const char* s, size_t start, size_t length) {
    unsigned int len = strlen(s);
    if (start >= len)
        return NULL;
    if (start + length > len)
        return __str_duplicate(s + start);

    char* ret = (char*)calloc(length + 1, sizeof(char));
    return strncpy(ret, s + start, length);
}

static char** __str_split_string_any(char* s, const char* s2, size_t* num) {
    const char* find;
    if (s2 == NULL)
        find = " \n\r\f\v\t";
    else
        find = s2;

    size_t max_size = __str_find_cnt_any(s, find);
    char** results = (char**)calloc(max_size + 1,  sizeof(char*));
    char* loc = s;
    int cnt = 0;
    int len = __str_find_any(loc, find);

    while (len != -1) {
        if (len == 0) {
            loc[0] = '\0';
            len = __str_find_any(++loc, find);
            continue;
        }
        results[cnt++] = loc;
        loc += len;
        loc[0] = '\0';
        len = __str_find_any(++loc, find);
    }
    if (loc[0] != '\0')
        results[cnt++] = loc;
    *num = cnt;

    char** v = (char**)realloc(results, cnt * sizeof(char*));
    if (v == NULL)
        return results;
    return v;
}

static int __str_find_any(const char* s, const char* s2) {
    char* loc = strpbrk((char*)s, s2);
    if (loc == NULL)
        return -1;
    return loc - s;
}

static size_t __str_find_cnt_any(const char* s, const char* s2) {
    size_t res = 0;
    char* loc = strpbrk((char*)s, s2);
    while (loc != NULL) {
        ++res;
        loc = strpbrk(loc + 1, s2);
    }
    return res;
}

static void __free_double_array(char** arr, size_t num_elms) {
    size_t i;
    for (i = 0; i < num_elms; ++i) {
        free(arr[i]);
    }
    free(arr);
}

static void __parse_file_info(const char* full_filepath, char** filepath, char** filename) {
    /* ensure path and filename are not leaking memory */
    free(*filepath);
    free(*filename);

    if (full_filepath == NULL || strlen(full_filepath) == 0) {
        (*filepath) = NULL;
        (*filename) = NULL;
        return;
    }

    int pathlen = strlen(full_filepath);

    int slash_loc = __str_find_reverse(full_filepath, '/');
    if (slash_loc == -1) {
        (*filepath) = __str_duplicate(".");
        (*filename) = __str_duplicate(full_filepath);
        return;
    }

    *filepath = __str_extract_substring(full_filepath, 0, slash_loc + 1);
    *filename = __str_extract_substring(full_filepath, slash_loc + 1, pathlen);
    return;
}

static int __cmp_str(const void* a, const void* b) {
    return strcmp(*(const char**)a, *(const char**)b);
}

static int is_symlink(struct stat *stats) {
    if (S_ISLNK(stats->st_mode) != 0)
        return FS_SUCCESS;
    return FS_FAILURE;
}
