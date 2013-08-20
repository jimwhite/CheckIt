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
       char * envp[] = { 
            "PATH=/bin:/usr/bin"
            , "HOME=/home2/ling572_00"
            , "JAVA_HOME=/usr/java/latest"
            , "JAVA_OPTS=-Xmx300m -Xms140m"
            , "LC_ALL=en_US.UTF-8"
            , user_evar
            , (char *) 0 };

       // Do it!
       errno = execve("/home2/ling572_00/Projects/CheckIt/bin/check_it.groovy", argv, envp);
    }

    printf("An error occured %d\n", errno);  

    return errno;  
}  
