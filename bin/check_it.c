#include <stdio.h>  
#include <unistd.h>  
  
int main(int argc, char** argv) {  
    const char * envp[2];
    envp[0] = "PATH=/bin:/usr/bin";
    envp[1] = 0;
    int errno = 0;
    errno = setreuid(geteuid(), geteuid());
//    errno = execle("/usr/bin/id", (char *) 0, envp);  
    if (errno == 0) {
       errno = execle("/home2/jimwhite/Projects/Groovy/groovy-2.1.6/bin/groovy", "-f", "/home2/ling572_00/CheckIt/bin/check_it.groovy", "ls ; env", (char *) 0, envp);  
//       errno = execle("/home2/ling572_00/CheckIt/bin/howdy.sh", (char *) 0, envp);  
    }
    if (errno != 0) printf("An error occured %d\n", errno);  
    return errno;  
}  
