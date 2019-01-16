#include <stdio.h>
#include <stdlib.h>
#include <sys/socket.h>
#include <netdb.h>
#include <netinet/in.h>
#include <stdbool.h>
#include <string.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <sys/types.h>

enum socks 
{
	RESERVED = 0x00,
	VERSION = 0x05
};

enum socks_methods 
{
	NOAUTH = 0x00,
	NOMETHOD = 0xff
};

enum socks_command 
{
	CONNECT = 0x01
};

enum socks_adr_type 
{
	IPV4 = 0x01,
	DOMAIN = 0x03
};

enum socks_status {
	OK = 0x00,
	CONNECTION_REFUSED = 0x05,
	COMMAND_NOT_SUPPORTED = 0x07,
	ATYPE_NOT_SUPPORTED = 0x08
};

bool readn(int fd, void *buf, int size);
char socks5_invitation(int fd);
bool socks5_set_method(int fd, char nmethods);
int connect_to_server(char *node, char *port);
char socks5_type_read(int fd);
char* socks5_read_port(int fd);
char *socks5_ip_read(int fd);
char *socks5_domain_read(int fd, unsigned char *size);
void socks5_send_response(int fd, char rep);
