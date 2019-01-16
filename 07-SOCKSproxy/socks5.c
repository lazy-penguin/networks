#include "socks5.h"

bool readn(int fd, void *buf, int size)
{
	int readn, offset = 0;
	while (offset != size) 
	{
		readn = read(fd, buf + offset, size - offset);
		if (readn == -1) 
		{
			perror("read()");
			return false;
		} 
		offset += readn;	
	}
	return true;
}

char socks5_invitation(int fd)
{
	char init[2];	//VERSION, NMETHODS
	if(!readn(fd, init, 2))
		return -1;
	
	if (init[0] != VERSION) 
	{
		fprintf(stderr, "Not supported version");
		return -1;
	}
	return init[1];
}

bool socks5_set_method(int fd, char nmethods)
{
	bool supported = false;
	char auth = NOMETHOD;
	for (int i = 0; i < nmethods; i++) 
	{
		char type;
		readn(fd, (void *)&type, 1);
		if (type == NOAUTH) 
		{
			supported = true;
			auth = type;
			break;
		}
	}

	char answer[2] = { VERSION, auth };
	if(!write(fd, answer, 2))
	{
		perror("write()");
		return false;
	}
	return supported;
}

int connect_to_server(char *node, char *port)
{
	struct addrinfo hints, *res, *addr_buf;
	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;

	int ret = getaddrinfo(node, port, &hints, &res);
	if (ret)
	{
		fprintf(stderr, "getaddrinfo: %s\n", gai_strerror(ret));
		return -1;
	}
	int fd;
	for(addr_buf = res; addr_buf != NULL; addr_buf = addr_buf->ai_next) 
	{
    	fd = socket(addr_buf->ai_family, addr_buf->ai_socktype, addr_buf->ai_protocol);
		if(fd == -1)
			continue;
		if (!connect(fd, addr_buf->ai_addr, addr_buf->ai_addrlen))
	        break;
	    else
	    	perror("connect()");
		close(fd);
	}
	freeaddrinfo(res);
	if(addr_buf == NULL)
	{
		fprintf(stderr, "Cannot connect to server\n");
	    return -1;
	}
	return fd;
}

char socks5_type_read(int fd)
{
	char command[4];
	if(!readn(fd, command, 4))
		return -1;
	if(command[1] != CONNECT)
	{
		fprintf(stderr, "Not supported command");
		return -1;
	}
	return command[3];
}

char *socks5_ip_read(int fd)
{
	char *ip = malloc(4);		//ip size
	if(!readn(fd, ip, 4))
	{	
		free(ip);
		return NULL;
	}
	char *dest_addr = malloc(16);
	snprintf(dest_addr, 16, "%hhu.%hhu.%hhu.%hhu", ip[0], ip[1], ip[2], ip[3]);
	free(ip);
	return dest_addr;
}

char *socks5_domain_read(int fd, unsigned char *size)
{
	unsigned char size_buf;
	if(!readn(fd, &size_buf, sizeof(size_buf)))
		return NULL;

	char *address = malloc(size_buf + 1);
	if(!readn(fd, address, (int)size_buf))
	{
		free(address);
		return NULL;
	}

	address[size_buf] = '\0';
	*size = size_buf;
	return address;
}

char* socks5_read_port(int fd)
{
	unsigned short int port;
	if(!readn(fd, &port, 2))
		return NULL;
	port = ntohs(port);
	char *dest_port = malloc(2);
	snprintf(dest_port, 6, "%d", port);
	return dest_port;
}

void socks5_send_response(int fd, char rep)
{	
	struct sockaddr_in bind_addr;
	char bind_ip[16];
	int len = sizeof(bind_addr);
	getsockname(fd, (struct sockaddr*)&bind_addr, &len);
	inet_ntop(AF_INET, &bind_addr.sin_addr, bind_ip, sizeof(bind_ip));

	unsigned short int port = htons(bind_addr.sin_port);
	char response[4] = { VERSION, rep, RESERVED, IPV4 };
	write(fd, response, 4);
	write(fd, bind_ip, 4);
	write(fd, &port, sizeof(port));
}