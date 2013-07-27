#include <stdio.h>  
#include <unistd.h>  
#include <stdlib.h>
  
int main(int argc, char** argv) {  
    int errno = setreuid(geteuid(), geteuid());
//    errno = execle("/usr/bin/id", (char *) 0, envp);  
    if (errno == 0) {
       char * envp[] = { "PATH=/bin:/usr/bin", (char *) 0 };
       char ** gargv = (char **) calloc((argc + 3), sizeof(char *));
       
       gargv[0] = "/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy";
       gargv[1] = "/home2/ling572_00/CheckIt/bin/check_it.groovy";
       
       int i;
       for (i = 1; i < argc; ++i) gargv[i + 1] = argv[i];
       
       for (i = 0; envp[i] ; ++i) printf("env[%d] : %s\n", i, envp[i]);
       for (i = 0; gargv[i]; ++i) printf("argv[%d] : %s\n", i, gargv[i]);
       
       errno = execve(gargv[0], gargv, envp);  
    }

    printf("An error occured %d\n", errno);  

    return errno;  
}  
