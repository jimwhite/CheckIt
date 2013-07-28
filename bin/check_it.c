#include <stdio.h>  
#include <unistd.h>  
#include <stdlib.h>
#include <pwd.h>

int main(int argc, char** argv) {
    struct passwd *passwd = getpwuid(getuid());
    int errno = setreuid(geteuid(), geteuid());

//    errno = execle("/usr/bin/id", (char *) 0, envp);

    if (errno == 0 && passwd != 0) {
       // CHECKIT_USER will contain the name of the user that invoked us.
       char user_evar[100];
       snprintf(user_evar, 80, "CHECKIT_USER=%s", passwd->pw_name);

       // Use a nice clean PATH.
       char * envp[] = { "PATH=/bin:/usr/bin", user_evar, (char *) 0 };

       char ** gargv = (char **) calloc((argc + 3), sizeof(char *));

       // We'll run ling572_00's check_it.groovy as ling572_00.
       gargv[0] = "/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy";
       gargv[1] = "/home2/ling572_00/Projects/CheckIt/bin/check_it.groovy";
       // gargv[2] = passwd->pw_name;

       // Copy the args given by our caller.
       int i;
       for (i = 1; i < argc; ++i) gargv[i + 1] = argv[i];
       
       // for (i = 0; envp[i] ; ++i) printf("env[%d] : %s\n", i, envp[i]);
       // for (i = 0; gargv[i]; ++i) printf("argv[%d] : %s\n", i, gargv[i]);

       // Do it!
       errno = execve(gargv[0], gargv, envp);  
    }

    printf("An error occured %d\n", errno);  

    return errno;  
}  
