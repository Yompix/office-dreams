// Тестовая заглушка: в песочнице нет IPv6. Превращаем IPv6-сокеты BDS в IPv4 на соседнем порту.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <string.h>
#include <errno.h>
static int (*r_socket)(int,int,int); static int (*r_bind)(int,const struct sockaddr*,socklen_t);
static int (*r_setsockopt)(int,int,int,const void*,socklen_t);
static int v6fd[65536];
int socket(int d,int t,int p){ if(!r_socket) r_socket=dlsym(RTLD_NEXT,"socket"); if(d==AF_INET6){int fd=r_socket(AF_INET,t,p==IPPROTO_IPV6?0:p); if(fd>=0&&fd<65536) v6fd[fd]=1; return fd;} return r_socket(d,t,p);}
int bind(int fd,const struct sockaddr*a,socklen_t l){ if(!r_bind) r_bind=dlsym(RTLD_NEXT,"bind");
  if(a&&a->sa_family==AF_INET6){ struct sockaddr_in s; memset(&s,0,sizeof s); s.sin_family=AF_INET; s.sin_port=((struct sockaddr_in6*)a)->sin6_port; s.sin_addr.s_addr=htonl(0x7f000002); return r_bind(fd,(struct sockaddr*)&s,sizeof s);} return r_bind(fd,a,l);}
int setsockopt(int fd,int lv,int n,const void*v,socklen_t l){ if(!r_setsockopt) r_setsockopt=dlsym(RTLD_NEXT,"setsockopt"); if(lv==IPPROTO_IPV6) return 0; return r_setsockopt(fd,lv,n,v,l);}
